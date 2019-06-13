package wang.relish.butterknife;

/**
 * BindView注解的View的相关信息
 *
 * @author Relish Wang
 * @since 20190611
 */
class BindInfo {
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
