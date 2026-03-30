package com.neonide.studio.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.neonide.studio.R;
import com.neonide.studio.app.home.clone.CloneRepositoryDialogFragment;

public class MainMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        setupButtons();
    }

    private void setupButtons() {
        findViewById(R.id.btn_create_project).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.btn_open_project).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.btn_clone_repo).setOnClickListener(v -> new CloneRepositoryDialogFragment().show(getSupportFragmentManager(), "clone_repo"));
        findViewById(R.id.btn_preferences).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.btn_ide_config).setOnClickListener(v -> startActivity(new Intent(this, IdeConfigActivity.class)));
        findViewById(R.id.btn_docs).setOnClickListener(v -> showComingSoon());

        Button terminalButton = findViewById(R.id.btn_terminal);
        terminalButton.setOnClickListener(v -> startActivity(new Intent(this, TermuxActivity.class)));

        Button setupDevKitButton = findViewById(R.id.btn_setup_dev_kit);
        setupDevKitButton.setOnClickListener(v -> DevKitSetup.startSetup(this));
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.msg_feature_coming_soon, Toast.LENGTH_SHORT).show();
    }
}
