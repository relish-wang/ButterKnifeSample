package wang.relish.butterknife;

import android.app.Activity;

import java.lang.reflect.Method;

/**
 * 用于在Activity中注册
 * @author Relish Wang
 * @since 20190611
 */
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
