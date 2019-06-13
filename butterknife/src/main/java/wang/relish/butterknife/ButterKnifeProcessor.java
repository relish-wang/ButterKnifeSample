package wang.relish.butterknife;

import android.app.Activity;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

/**
 * 注解处理器
 * <p/>
 * 负责遍历注解后把收集到的信息生成对应的findViewById的类,
 * 再由{@link ButterKnife#bind(Activity)}去调用, 从而实现依赖注入
 *
 * @author Relish Wang
 * @since 20190611
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8) // 支持的Java版本
@SupportedAnnotationTypes({"wang.relish.butterknife.BindView"}) // 支持的注解。可添加多个，用逗号隔开
@AutoService(Processor.class) // 向javac注册自定义的注解处理器
public class ButterKnifeProcessor extends AbstractProcessor {

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //===================================【① 注解分拣】==========================================
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
            //===============================【② 生成代码】==========================================
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
            //=================================【③ 写入文件】========================================
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

                // 调试用, 打印文件内容。(打印在AS底部Build->Build Output窗口中)
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
