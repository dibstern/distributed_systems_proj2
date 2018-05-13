package activitystreamer.client;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import activitystreamer.util.GridLayout2;

@SuppressWarnings("serial")
public class TextFrame extends JFrame implements ActionListener {
    private static final Logger log = LogManager.getLogger();
    private JTextArea inputText;
    private JTextArea outputText;
    private JTextArea receivedText;
    private JTextArea usernameText;
    private JTextArea secretText;
    private JButton sendButton;
    private JButton logoutButton;
    private JSONParser parser = new JSONParser();

    /**
     * The constructor, builds the GUI and starts it.
     */
    public TextFrame() {
        setTitle("ActivityStreamer Text I/O");
        JPanel mainPanel = new JPanel();
        // Use Bogdan Dorohonceanu's GridLayout2 as in:     (GridBagLayout functionality w/ simpler GridLayout setup)
        // https://www.javaworld.com/article/2077486/core-java/java-tip-121--flex-your-grid-layout.html
        mainPanel.setLayout(new GridLayout2(0, 3, 4, 2));

        // Input Panel (to send Activity Stream Messages)
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());
        Border lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray), "JSON input, to send to server");
        inputPanel.setBorder(lineBorder);
        inputPanel.setName("JSON text input");
        inputPanel.setPreferredSize(new Dimension(200, 400));
        inputText = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(inputText);
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        // Buttons added to inputPanel
        JPanel buttonGroup = new JPanel();
        sendButton = new JButton("Send");
        logoutButton = new JButton("Disconnect");
        buttonGroup.add(sendButton);
        buttonGroup.add(logoutButton);
        inputPanel.add(buttonGroup, BorderLayout.SOUTH);
        sendButton.addActionListener(this);
        logoutButton.addActionListener(this);

        // Output panel (Received Activity Stream Messages, originally sent from us to server)
        JPanel outputPanel = new JPanel();
        outputPanel.setLayout(new BorderLayout());
        lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray), "JSON output, received from server");
        outputPanel.setBorder(lineBorder);
        outputPanel.setName("Text output");
        outputPanel.setPreferredSize(new Dimension(200, 400));
        outputText = new JTextArea();
        scrollPane = new JScrollPane(outputText);
        outputPanel.add(scrollPane, BorderLayout.CENTER);

        // Create a username panel
        JPanel usernamePanel = new JPanel();
        usernamePanel.setLayout(new BorderLayout());
        lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray), "Username");
        usernamePanel.setBorder(lineBorder);
        usernamePanel.setName("Username");
        usernamePanel.setPreferredSize(new Dimension(100, 25));
        usernameText = new JTextArea();
        scrollPane = new JScrollPane(usernameText);
        usernamePanel.add(scrollPane, BorderLayout.SOUTH);

        // Create a 'secret' (password) panel
        JPanel secretPanel = new JPanel();
        secretPanel.setLayout(new BorderLayout());
        lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray), "Secret");
        secretPanel.setBorder(lineBorder);
        secretPanel.setName("Secret");
        secretPanel.setPreferredSize(new Dimension(100, 25));
        secretText = new JTextArea();
        scrollPane = new JScrollPane(secretText);
        secretPanel.add(scrollPane, BorderLayout.SOUTH);

        // Received panel to show all received messages (aside from Activity Stream messages)
        JPanel receivedPanel = new JPanel();
        receivedPanel.setLayout(new BorderLayout());
        lineBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.lightGray), "All JSON messages received from server");
        receivedPanel.setBorder(lineBorder);
        receivedPanel.setName("All Received Server Messages");
        receivedPanel.setPreferredSize(new Dimension(200, 400));
        receivedText = new JTextArea();
        scrollPane = new JScrollPane(receivedText);
        receivedPanel.add(scrollPane, BorderLayout.CENTER);

        // Add all panels to main panel
        mainPanel.add(inputPanel);
        mainPanel.add(outputPanel);
        mainPanel.add(receivedPanel);
        mainPanel.add(usernamePanel);
        mainPanel.add(secretPanel);
        add(mainPanel);

        // Set up main panel
        setLocationRelativeTo(null);
        setSize(1280, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }


    /**
     * Updates the UI component using EventDispatchThread, in order to prevent threading issues
     * Adapted from Source: https://stackoverflow.com/a/4507800
     *
     * @param text (String) The message to be set or appended to logArea, the JTextArea.
     * @param logArea (JTextArea) The area where the text is to be placed.
     * @param append (boolean) If true, append to logArea, else replace the existing text to the text argument.
     * @param scroll (boolean) If true, scroll cursor to bottom of the logArea after updating.
     */
    public synchronized void log(final String text, final JTextArea logArea, final boolean append, final boolean scroll) {
        Runnable runnable = new Runnable() {
            public void run(){
                // Append to the existing text
                if (append) {
                    logArea.append(text + "\n");
                    if (logArea.getDocument().getLength() > 50000) {
                        try {
                            logArea.getDocument().remove(0, 5000);
                        }
                        catch (BadLocationException e) {
                            log.error("Can't clean log", e);
                        }
                    }
                }
                // Reset the text with the new text
                else {
                    logArea.setText(text);
                    if (scroll) {
                        logArea.append("\n");
                    }
                }
                logArea.revalidate();
                logArea.repaint();

                // Set the caret position - bottom of pane if scroll, otherwise the top of the pane.
                if (scroll) {
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
                else {
                    logArea.setCaretPosition(0);
                }
            }
        };
        SwingUtilities.invokeLater(runnable);
    }

    /**
     * Sets output text in the GUI for Messages received from the server.
     * @param obj (JSONObject) The json message to be set as text in the GUI.
     * @param isActivityMessage (boolean) If true then display the text in the middle text area, if false then the
     *                          rightmost text area.
     */
    public void setOutputText(final JSONObject obj, boolean isActivityMessage) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(obj.toJSONString());
        String prettyJsonString = gson.toJson(je);
        if (isActivityMessage) {
            log(prettyJsonString, outputText, false, true);
        }
        else {
            log(prettyJsonString, receivedText, true, true);
        }
    }

    public void showClientMessage(String msg) {
        log(msg, receivedText, true, true);
    }

    /**
     * Sets the username and secret boxes in the GUI to the current value for the client user.
     * @param username (String) The username of the client.
     * @param secret (String) The matching secret (password) of the client.
     */
    public void updateLoginInfo(String username, String secret) {
        if (username != null) {
            log(username, usernameText, false, false);
            // usernameText.setText(username);
            if (secret != null) {
                log(secret, secretText, false, false);
                // secretText.setText(secret);
            }
            else {
                log("No secret used", secretText, false, false);
                // secretText.setText("No secret used");
            }
        }
    }

    /**
     * Sets up the behaviour for the two buttons in the GUI.
     * sendButton: Tells the client to send the input text in the input text area to the server as an activity message.
     * logoutButton: Tells the client to logout of the client and close the GUI.
     * @param e (ActionEvent) The action received from the action listeners added to the buttons in the GUI.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendButton) {
            String msg = inputText.getText().trim().replaceAll("\r", "").replaceAll("\n", "").replaceAll("\t", "");
            JSONObject obj;
            try {
                obj = (JSONObject) parser.parse(msg);
                ConnectionManager.getInstanceClientConnection().sendActivityObject(obj);
            }
            catch (ParseException e1) {
                log.error("invalid JSON object entered into input text field, data not sent");
            }
        }
        else if (e.getSource() == logoutButton) {
            ConnectionManager.getInstanceClientConnection().logout();
            System.exit(0);
        }
    }
}
