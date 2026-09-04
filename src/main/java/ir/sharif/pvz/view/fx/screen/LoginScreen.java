package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.LoginMenuController;
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

    /**
     * Whether the player asked to recover rather than sign in. Which step of
     * the recovery they are on comes from the menu itself, because submitting
     * a command rebuilds this screen and anything kept here would be lost.
     */
    private boolean recovering;

    public LoginScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        Label title = new Label("Plants vs. Zombies 2");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Welcome back");
        subtitle.getStyleClass().add("brand-subtitle");

        LoginMenuController menu = (LoginMenuController) ui.app().currentController();
        VBox form = recovering || menu.isRecovering() ? recoveryPanel(menu) : loginPanel();
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

    /**
     * The three steps of recovery, one at a time: find the account, answer its
     * question, then set a new password. Which one shows is the menu's own
     * state rather than this screen's, because submitting a command builds a
     * fresh screen and anything remembered here would be gone.
     */
    private VBox recoveryPanel(LoginMenuController menu) {
        if (menu.isAwaitingNewPassword()) {
            return newPasswordStep();
        }
        return menu.isRecovering() ? answerStep(menu) : findAccountStep();
    }

    /**
     * Step one: name the account being recovered.
     */
    private VBox findAccountStep() {
        TextField username = new TextField();
        TextField email = new TextField();

        Button ask = new Button("Show my question");
        ask.getStyleClass().add("primary-button");
        ask.setDefaultButton(true);
        ask.setOnAction(event -> ui.submit("forget password -u "
                + SignupScreen.blankToDash(username.getText())
                + " -e " + SignupScreen.blankToDash(email.getText())));

        return Forms.panel(14,
                Forms.heading("Recover your password"),
                Forms.hint("We will show the question you picked when you signed up."),
                Forms.field("Username", username),
                Forms.field("Email", email),
                new HBox(12, ask, backButton()));
    }

    /**
     * Step two: the question, and a box to answer it in.
     */
    private VBox answerStep(LoginMenuController menu) {
        TextField answer = new TextField();
        Button send = new Button("Submit answer");
        send.getStyleClass().add("primary-button");
        send.setDefaultButton(true);
        send.setOnAction(event ->
                ui.submit("answer -a " + SignupScreen.blankToDash(answer.getText())));

        Label question = new Label(menu.recoveryQuestion());
        question.getStyleClass().add("field-label");
        question.setWrapText(true);

        return Forms.panel(14,
                Forms.heading("Recover your password"),
                question,
                Forms.field("Answer", answer),
                new HBox(12, send, backButton()));
    }

    /**
     * Step three, which had no screen at all: the menu asked for a new
     * password and there was nowhere to type one.
     */
    private VBox newPasswordStep() {
        PasswordField fresh = new PasswordField();
        Button save = new Button("Set my new password");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        save.setOnAction(event -> {
            ui.submit(SignupScreen.blankToDash(fresh.getText()));
            recovering = false;
        });

        return Forms.panel(14,
                Forms.heading("Choose a new password"),
                Forms.hint("At least eight characters, with a capital, a digit and a symbol."),
                Forms.field("New password", fresh),
                new HBox(12, save, backButton()));
    }

    private Button backButton() {
        Button back = new Button("Back to sign in");
        back.getStyleClass().add("link-button");
        back.setOnAction(event -> {
            recovering = false;
            ui.submit("cancel recovery");
        });
        return back;
    }

}
