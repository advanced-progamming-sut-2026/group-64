package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.controller.SignupMenuController;
import ir.sharif.pvz.model.SecurityQuestion;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Registration, in the two steps the phase-1 controller expects: the account
 * details first, then the security question.
 */
public final class SignupScreen extends Screen {

    private static final double FORM_WIDTH = 420;

    public SignupScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        SignupMenuController controller = (SignupMenuController) ui.app().currentController();
        VBox form = controller.isAwaitingSecurityQuestion() ? securityStep() : detailsStep();
        form.setMaxWidth(FORM_WIDTH);
        form.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Plants vs. Zombies 2");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Create your account");
        subtitle.getStyleClass().add("brand-subtitle");

        VBox column = new VBox(18, title, subtitle, form);
        column.setAlignment(Pos.CENTER);
        column.setPadding(new Insets(40));

        StackPane root = new StackPane(column);
        root.getStyleClass().addAll("screen", "auth-screen");
        return root;
    }

    private VBox detailsStep() {
        TextField username = new TextField();
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        TextField nickname = new TextField();
        TextField email = new TextField();
        ChoiceBox<String> gender = new ChoiceBox<>();
        gender.getItems().addAll("male", "female");
        gender.setValue("male");

        Button create = new Button("Create account");
        create.getStyleClass().add("primary-button");
        create.setDefaultButton(true);
        create.setOnAction(event -> ui.submit(String.format(
                "register -u %s -p %s %s -n %s -e %s -g %s",
                blankToDash(username.getText()), blankToDash(password.getText()),
                blankToDash(confirm.getText()), blankToDash(nickname.getText()),
                blankToDash(email.getText()), gender.getValue())));

        Button toLogin = new Button("I already have an account");
        toLogin.getStyleClass().add("link-button");
        toLogin.setOnAction(event -> ui.enter(MenuType.LOGIN));

        return Forms.panel(14,
                Forms.field("Username", username),
                Forms.field("Password", password),
                Forms.field("Repeat password", confirm),
                Forms.field("Nickname", nickname),
                Forms.field("Email", email),
                Forms.field("Gender", gender),
                Forms.hint("Passwords need an uppercase letter, a digit and a symbol."),
                new HBox(12, create, toLogin));
    }

    private VBox securityStep() {
        ChoiceBox<String> question = new ChoiceBox<>();
        question.getItems().addAll(SecurityQuestion.all());
        question.setValue(SecurityQuestion.all().get(0));
        TextField answer = new TextField();
        TextField confirm = new TextField();

        Button finish = new Button("Finish");
        finish.getStyleClass().add("primary-button");
        finish.setDefaultButton(true);
        finish.setOnAction(event -> ui.submit(String.format("pick question -q %d -a %s -c %s",
                question.getSelectionModel().getSelectedIndex() + 1,
                blankToDash(answer.getText()), blankToDash(confirm.getText()))));

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("link-button");
        cancel.setOnAction(event -> ui.submit("cancel"));

        return Forms.panel(14,
                Forms.heading("One last step"),
                Forms.hint("Pick a security question so you can recover your password later."),
                Forms.field("Question", question),
                Forms.field("Answer", answer),
                Forms.field("Repeat answer", confirm),
                new HBox(12, finish, cancel));
    }

    /**
     * The phase-1 commands are whitespace separated, so an empty box has to
     * become a placeholder the validators can reject with a proper message.
     */
    static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
