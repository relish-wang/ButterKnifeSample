package wang.relish.butterknife.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import wang.relish.butterknife.BindView;
import wang.relish.butterknife.ButterKnife;

/**
 * ButterKnife应用Demo
 *
 * @author Relish Wang
 * @since 20190611
 */
public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tv1)
    TextView tv1;
    @BindView(R.id.tv2)
    TextView tv2;
    @BindView(R.id.tv3)
    TextView tv3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        tv1.setText("Welcome!");
        tv2.setText("This is the magic of:");
        tv3.setText("ButterKnife!");
    }
}
