package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Signing in, plus the password recovery flow from phase 1 shown in a floating
 * panel rather than on a page of its own.
 */
public final class LoginScreen extends Screen {

    private static final double FORM_WIDTH = 420;

    private boolean recovering;
    private boolean answering;

    public LoginScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        Label title = new Label("Plants vs. Zombies 2");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Welcome back");
        subtitle.getStyleClass().add("brand-subtitle");

        VBox form = recovering ? recoveryPanel() : loginPanel();
        form.setMaxWidth(FORM_WIDTH);

        VBox column = new VBox(18, title, subtitle, form);
        column.setAlignment(Pos.CENTER);
        column.setPadding(new Insets(40));

        StackPane root = new StackPane(column);
        root.getStyleClass().addAll("screen", "auth-screen");
        return root;
    }

    private VBox loginPanel() {
        TextField username = new TextField();
        PasswordField password = new PasswordField();
        CheckBox stay = new CheckBox("Keep me signed in");

        Button login = new Button("Sign in");
        login.getStyleClass().add("primary-button");
        login.setDefaultButton(true);
        login.setOnAction(event -> ui.submit("login -u "
                + SignupScreen.blankToDash(username.getText())
                + " -p " + SignupScreen.blankToDash(password.getText())
                + (stay.isSelected() ? " -stay-logged-in" : "")));

        Button forgot = new Button("Forgot password?");
        forgot.getStyleClass().add("link-button");
        forgot.setOnAction(event -> {
            recovering = true;
            ui.show(this);
        });

        Button register = new Button("Create an account");
        register.getStyleClass().add("link-button");
        register.setOnAction(event -> ui.enter(MenuType.SIGNUP));

        return Forms.panel(14,
                Forms.field("Username", username),
                Forms.field("Password", password),
                stay,
                new HBox(12, login, forgot, register));
    }

    private VBox recoveryPanel() {
        TextField username = new TextField();
        TextField email = new TextField();
        TextField answer = new TextField();

        Button ask = new Button("Show my question");
        ask.getStyleClass().add("primary-button");
        ask.setOnAction(event -> {
            ui.submit("forget password -u " + SignupScreen.blankToDash(username.getText())
                    + " -e " + SignupScreen.blankToDash(email.getText()));
            answering = true;
        });

        Button send = new Button("Submit answer");
        send.getStyleClass().add("primary-button");
        send.setDisable(!answering);
        send.setOnAction(event ->
                ui.submit("answer -a " + SignupScreen.blankToDash(answer.getText())));

        Button back = new Button("Back to sign in");
        back.getStyleClass().add("link-button");
        back.setOnAction(event -> {
            recovering = false;
            answering = false;
            ui.show(this);
        });

        return Forms.panel(14,
                Forms.heading("Recover your password"),
                Forms.hint("Your security question appears as a notification once we find your account."),
                Forms.field("Username", username),
                Forms.field("Email", email),
                new HBox(12, ask),
                Forms.field("Answer", answer),
                new HBox(12, send, back));
    }
}
