package org.openaccessbutton.openaccessbutton.intro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.openaccessbutton.openaccessbutton.R;
import org.openaccessbutton.openaccessbutton.menu.MenuActivity;

public class SigninActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        // Until we get an authentication method in the API (TODO), just go straight to the main
        // activity whenever the login button is pressed
        Button loginButton = (Button) findViewById(R.id.signinButton);
        final Context context = this;
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to MenuActivity
                Intent k = new Intent(context, MenuActivity.class);
                startActivity(k);
                finish();
            }
        });


        // Social signin buttons
        TextView googleButton = (TextView) findViewById(R.id.signupGoogleButton);
        googleButton.setOnClickListener(new SignUpSocialMediaClickListener(this, "google"));
        TextView facebookButton = (TextView) findViewById(R.id.signupFacebookButton);
        facebookButton.setOnClickListener(new SignUpSocialMediaClickListener(this, "facebook"));
        TextView twitterButton = (TextView) findViewById(R.id.signupTwitterButton);
        twitterButton.setOnClickListener(new SignUpSocialMediaClickListener(this, "twitter"));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.signin, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
