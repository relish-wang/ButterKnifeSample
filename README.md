# ButterKnife最简实现

![][banner]

> ButterKnife用了这么多年，你到底了解多少？

[TOC]

近来研究APT(Annotation Processing Tool)技术的实现场景和实际应用，想到可以先了解一下APT技术应用的先驱框架们。最具代表性的就是[ButterKnife][butterknife]。

网络上大多数介绍ButterKnife简单实现的例子都写得很繁琐, 非要区分compiler、annotation、runtime几个module; 要不就是较早的文章，还用到了[android-apt](https://bitbucket.org/hvisser/android-apt)插件(该插件已被Android Gradle 2.2所替代, 其作者也不再维护); 还有的非要在例子里用[JavaPoet](https://github.com/square/javapoet), 增加新手的学习成本。本来就是想学一下**注解**和**注解处理器**，看完后还是云里雾里。

我就~~和这些妖艳x货~~不一样，我就喜欢怎么简单怎么来。我写的Sample，算上注释也才200多行代码(~~不包括Demo和AS自动生成的代码~~)。

## 主要原料

开始讲解之前，先说一下`AbstractProcessor`(注解处理器)和`Annotation`(注解)。~~其实可以跳过, 建议先看看ButterKnife的工作流程图,  再决定值不值得花时间继续读这篇文章~~

**注解**提供了一种为程序元素（类、方法、成员变量等）设置元数据（描述其它数据的数据）的方式。

**元注解**: 简单的说就是，用来修饰自定义的注解的注解。JDK中一共6个元注解，今天我们要用到其中的两个——、`@Retention`和`@Target`。

**@Retention**:指定被修饰的Annotation保留的时间。

```java
//编译器把注解保存在class文件中，当运行java程序时，JVM不可获取注解信息(这是@Retention的默认值)
@Retention(value = RetentionPolicy.CLASS)
@interface TestAnnotation{}
 
//编译器把注解保存在class文件中，当运行java程序时，JVM可以获取注解信息
//程序可以通过反射获取该注释
@Retention(value = RetentionPolicy.RUNTIME)
@interface TestAnnotation{}
 
//注解保存在源码中，编译器直接丢弃该注解
@Retention(value = RetentionPolicy.SOURCE)
@interface TestAnnotation{}
```

**@Target**：指定被修饰的自定义注解能够修饰哪些程序单元。

```java
ElementType.TYPE: 被修饰的自定义注解可以修饰类、接口（包括注解）、枚举定义
ElementType.CONSTRUCTOR: 被修饰的自定义注解只能修饰构造方法
ElementType.METHOD: 被修饰的自定义注解只能修饰方法定义
ElementType.FIELD: 被修饰的自定义注解只能修饰成员变量
```
`ButterKnife`的`@BindView`注解就是一种运行时注解。

综上所述，**注解**就像是信息追踪器，用来收集信息，只要是被注解标记的元素，它的相关信息就会被采集。**注解处理器**就像是一个信息接收装置，对注解采集来的信息做处理。

## 一、ButterKnife工作流程

![flow][flow]



ButterKnife的工作流程如下:

- **① 采集注解信息**(编译时)

  前面说到**注解**(Annotation)就像是一个信息追踪器， **注解处理器**(AnnotationProcessor)就像是信息采集分析器。注解采集到了这个View相关的信息，全都传给注解处理器。

- **② 生成模板代码**(编译时)

  收集到的这些信息就可以用于生成findViewById的模板代码。**注解收集器**工作在编译环境因此可以在运行前生成模板文件。注解收集到的信息就派上用场了:
  
  ```java
  package wang.relish.butterknife.sample;
  
  public class MainActivity_BindView {
      public static void bind(MainActivity activity) {
          activity.textView = (android.widget.TextView) activity.findViewById(2131165315);
      }
  }
  ```

- **③ 加载模板类**(运行时)

  在`Activity`的`onCreate`中调用`ButterKnife#bind(Activity activity)`方法, `ButterKnife#bind(Activity activity)`方法实际上是通过传入的Activity的全类名加上模板类后缀，进而反射加载模板类，调用模板类的bind方法，完成findViewById的工作。



新建Java Library, 在build.gradle中添加android和auto-services的依赖，并且声明使用Java版本为1.8。

**butterknife/build.gradle**:

```groovy
apply plugin: 'java-library'
dependencies {
    implementation 'com.google.auto.service:auto-service:1.0-rc5'
    implementation 'com.google.android:android:4.1.1.4'
}
sourceCompatibility = JavaVersion.VERSION_1_8
targetCompatibility = JavaVersion.VERSION_1_8

```

## 二、ButterKnife工程搭建

下图是本工程中butterknife(library)所有文件一览:

![][butterknife_module]



### 新建library工程

新建`Java Library`(千万别选错了), 取名`butterknife`。

![][java_library]

填写必要信息后点击`Finish`。

![][create_butterknife]

新建完成的module长这样:

![][butterknife_module_create]

### 代码编写

**build.gradle**

首先我们来修改`build.gradle`文件

```groovy
apply plugin: 'java-library'

dependencies {
  	// 1 添加auto-services依赖
    implementation 'com.google.auto.service:auto-service:1.0-rc5'
    // 2 添加Android依赖
    implementation 'com.google.android:android:4.1.1.4'
}

// 3 使用Java8(都9102年了, 你还想用Java7吗？)
sourceCompatibility = JavaVersion.VERSION_1_8
targetCompatibility = JavaVersion.VERSION_1_8

```

**BindView.java**

标注在View上的自定义注解。

```java
@Target(ElementType.FIELD) // 标注在成员变量上
@Retention(RetentionPolicy.RUNTIME) // 运行时注解
public @interface BindView {
    int value(); // 用于采集View的resId
}

```

**BindInfo.java**

注解采集到的信息实体类。

采集的信息: **View变量名**、**View具体类型**、**View的ResId**。

```java
class BindInfo { // 不需要被库外部调用, 包级作用域就够了
    /**
     * findViewById代码模板
     */
    private static final String INJECTION = "        activity.%s = (%s) activity.findViewById(%s);";

    /**
     * 被注解的View的变量名
     */
    private final String variableName;
    /**
     * 被注解的View具体类型(比如: TextView、Button、ImageView)
     */
    private final String type;
    /**
     * 被注解的View的ResID
     */
    private final int value;

    BindInfo(String variableName, String type, int value) {
        this.variableName = variableName;
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format(INJECTION, variableName, type, value);
    }
}
```

**ButterKnifeProcessor.java**

新建BindView.java文件。这可是我们的重头戏了，注解采集来的信息处理都在这里面。

主要分三步进行：

- **① 注解信息分拣**

  注解可能来自不同的Activity，我们需要将它们根据所述的Activity区分开。分拣后的注解信息保存在`Map<TypeElement, Set<BindInfo>>`中(**Activity类型**->**该Activity的注解们的信息**的映射)。

- **② 生成代码**

  使用字符串拼接, 完成`findViewById`的工作。例：

  ```java
  package wang.relish.butterknife.sample;
  
  public class MainActivity_BindView {
      public static void bind(MainActivity activity) {
          activity.textView = (android.widget.TextView) activity.findViewById(2131165315);
      }
  }
  ```

- **③ 写入文件**

  Filer被翻译为**文件编档员，文件装订员**。Filer对象会将文件写入到build文件夹下，生成的文件会在运行时被加载。

```java
@SupportedSourceVersion(SourceVersion.RELEASE_8) // 支持的Java版本
@SupportedAnnotationTypes({"wang.relish.butterknife.BindView"}) // 支持的注解。可添加多个，用逗号隔开
@AutoService(Processor.class) // 向javac注册自定义的注解处理器
public class ButterKnifeProcessor extends AbstractProcessor {

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //============================【① 注解分拣】===================================
        // 根据类分类所有的BindView注解(映射关系: 某Activity->该Activity下的所有BindView集合)
        Map<TypeElement, Set<BindInfo>> injectionsByClass = new HashMap<>();
        // 被注解了@BindView的元素们(来自不同的Activity中的View注解)
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(BindView.class);
        // 遍历梳理所有的BindView注解(将它们归类到所属的类中)
        for (Element element : elements) {
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            // TODO 这里可以做一些类型校验的事, 但为了代码流程清晰, 故省略。
            // 获取当前元素(类)下的注解信息们(如果为空则新建)
            Set<BindInfo> injections = injectionsByClass.get(enclosingElement);
            if (injections == null) {
                injections = new HashSet<>();
                injectionsByClass.put(enclosingElement, injections);
            }

            // 1 View的变量名
            final String variableName = element.getSimpleName().toString();
            // 2 View的确定类型(如: TextView, Button, ImageView等)
            final String type = element.asType().toString();
            // 3 View的resId值
            final int value = element.getAnnotation(BindView.class).value();

            // 添加到分类集合中
            injections.add(new BindInfo(variableName, type, value));
        }

        for (Map.Entry<TypeElement, Set<BindInfo>> injection : injectionsByClass.entrySet()) {
            //=========================【② 生成代码】==================================
            final TypeElement type = injection.getKey();// 实际Activity类的类型
            final String targetClass = type.getQualifiedName().toString();// 实际Activity类的全类名
            int lastDot = targetClass.lastIndexOf(".");// "com.xxx.MainActivity"中最后一个点的位置
            // 原Activity类名
            String activityType = targetClass.substring(lastDot + 1);// 简单类名。如: MainActivity
            // 注入类类名
            String className = activityType + ButterKnife.SUFFIX;// 注入类的类名。如: MainActivity_BindView
            // 包名
            String packageName = targetClass.substring(0, lastDot); // 包名。如: com.xxx
            // 注入类全类名, 创建Java文件时用。例: com.xxx.MainActivity_BindView
            String fullClassName = packageName + "." + className;

            // 当前Activity下所有findViewById的代码
            StringBuilder injections = new StringBuilder();
            for (BindInfo injectionPoint : injection.getValue()) {
                // 例: activity.tv = (TextView) activity.findViewById(2131165325);
                injections.append(injectionPoint).append("\n");
            }
            //===========================【③ 写入文件】=================================
            try {
                Filer filer = processingEnv.getFiler();
                JavaFileObject javaFileObject = filer.createSourceFile(fullClassName, type);
                Writer writer = javaFileObject.openWriter();
                String javaFileContent = String.format(
                        INJECTOR,               // 模板
                        packageName,            // 包名
                        className,              // 注入类类名
                        activityType,           // 原Activity类名
                        injections.toString()); // findViewById的代码

                // 调试用(正式发布时删除), 打印文件内容。(打印在AS底部Build->Build Output窗口中)
                note(javaFileContent);

                writer.write(javaFileContent);
                writer.flush();
                writer.close();
            } catch (IOException e) {
                error(e.getMessage());
            }
        }
        return true;
    }

    /**
     * 注入类代码模板
     */
    private static final String INJECTOR = ""
            + "package %s;\n\n"                               // 包名。例: com.xxx
            + "public class %s {\n"                           // 注入类类名。例: MainActivity_BindView
            + "    public static void bind(%s activity) {\n"  // 类名。例: MainActivity
            + "%s"                                            // 当前所有的findViewById代码(可能多行)
            + "    }\n"
            + "}\n";

    private void error(String message, Object... args) {
        processingEnv.getMessager().printMessage(ERROR, String.format(message, args));
    }

    private void note(String message, Object... args) {
        processingEnv.getMessager().printMessage(NOTE, String.format(message, args));
    }
}
```

**ButterKnife.java**

反射调用模板类的`bind`方法, 完成findViewById工作。

```java
public final class ButterKnife {
    // 模板类后缀
    static final String SUFFIX = "_BindView";

    public static void bind(Activity activity) {
        try {
            // 根据 具体Activity的全类名+模板类后缀 找到 模板类的全类名
            Class<?> injector = Class.forName(activity.getClass().getName() + SUFFIX);
            // 获取模板类的bind方法 例: wang.relish.butterknife.sample.MainActivity_BindView#bind
            Method bindMethod = injector.getDeclaredMethod("bind", activity.getClass());
            // 反射调用模板类的bind方法，完成findViewById工作。
            bindMethod.invoke(null, activity);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unable to inject views for activity " + activity, e);
        }
    }
}
```

**javax.annotation.processing.Processor**

我们需要先在`butterknife/src/main/`下新建`resources`文件夹，再在`resources`下新建`META-INF`，再在`META-INF`下新建`services`文件夹, 再在`services`下新建`javax.annotation.processing.Processor`文件。(~~为什么用这么刻板的话来讲建多个文件夹的事?因为曾经有一个人建完`resources`文件夹后, 在其下又新建了一个名为`META-INF.services`的文件夹。此人的坟头草已经二尺高了…~~)

```shell
wang.relish.butterknife.ButterKnifeProcessor
```

## 三、如何使用

主工程(app)添加依赖:

```groovy
dependencies {
    implementation project(":butterknife")
    annotationProcessor project(":butterknife")
}
```

在Activity中使用:

```java
public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tv1) // 标记注解
    TextView tv1;
    @BindView(R.id.tv2)
    TextView tv2;
    @BindView(R.id.tv3)
    TextView tv3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
       
        // 去加载负责findViewById工作的模板类, 并执行
        ButterKnife.bind(this); 

        tv1.setText("Welcome!");
        tv2.setText("This is the magic of:");
        tv3.setText("ButterKnife!");
    }
}
```

`activity_main.xml`的代码就不贴了, 反正就三个TextView。需要查看的自行取用:[activity_main.xml][activity_main]

![][activity_main_shot]

运行成功后:

![][sample]

## 最简例子实现后的思考

至此我们仅实现了在`Activity`中`View`的注解工作。那么在`Fragment`中呢？在`ViewHolder`中呢？除了`@BindView`, `@OnClick`等事件监听该如何通过注解/注解处理器实现呢？这些疑问将在之后的文章中一一解答，敬请期待。

## 写在最后

APT的应用场景很广, `ButterKnife`的最简实现只是解开了它的冰山一角。日后还有更多的新姿势等着我们去解锁。**生命不息，学习不止。**




参考资料:

[google/auto-services源码](https://github.com/google/auto/tree/master/service)




[banner]: ./art/banner.png
[flow]: ./art/flow.png
[butterknife_module]: ./art/butterknife_module.png
[butterknife]: https://github.com/JakeWharton/butterknife
[java_library]: ./art/java_library.png
[create_butterknife]: ./art/create_butterknife.png
[butterknife_module_create]: ./art/butterknife_module_create.png
[activity_main]: ./app/src/main/res/layout/activity_main.xml
[activity_main_shot]: ./art/activity_main.png
[sample]: ./art/display.png