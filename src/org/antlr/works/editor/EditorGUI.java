/*

[The "BSD licence"]
Copyright (c) 2005 Jean Bovet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.antlr.works.editor;

import edu.usfca.xj.appkit.swing.XJTree;
import edu.usfca.xj.foundation.notification.XJNotificationCenter;
import edu.usfca.xj.foundation.notification.XJNotificationObserver;
import org.antlr.works.dialog.DialogPrefs;
import org.antlr.works.editor.analysis.AnalysisStrip;
import org.antlr.works.editor.rules.Rules;
import org.antlr.works.editor.swing.EditorStyledDocument;
import org.antlr.works.editor.swing.Gutter;
import org.antlr.works.editor.swing.TextEditorPane;
import org.antlr.works.editor.swing.TextEditorPaneDelegate;
import org.antlr.works.editor.tool.TAutoIndent;
import org.antlr.works.editor.tool.TImmediateColorization;
import org.antlr.works.editor.undo.Undo;
import org.antlr.works.editor.undo.UndoDelegate;
import org.antlr.works.parser.Lexer;
import org.antlr.works.parser.Parser;
import org.antlr.works.parser.Token;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.Iterator;

public class EditorGUI implements UndoDelegate, XJNotificationObserver, TextEditorPaneDelegate {

    public EditorWindow editor;

    public Gutter gutter;
    public AnalysisStrip analysisStrip;

    public JScrollPane textScrollPane;

    public JScrollPane rulesScrollPane;
    public XJTree rulesTree;

    public JTabbedPane viewTabbedPane;
    public JPanel mainPanel;

    public Box infoPanel;
    public JLabel infoLabel;
    public JLabel cursorLabel;
    public JLabel scmLabel;

    public JSplitPane rulesTextSplitPane;
    public JSplitPane upDownSplitPane;

    public TextEditorPane textPane;
    public TextPaneListener textPaneListener;

    public String lastSelectedRule;

    public static final String unixEndOfLine = "\n";

    protected TImmediateColorization immediateColorization;
    protected TAutoIndent autoIndent;

    protected boolean highlightCursorLine = false;

    public EditorGUI(EditorWindow editor) {
        this.editor = editor;
    }

    public void createInterface() {
        Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        r.width *= 0.75;
        r.height *= 0.75;
        editor.getRootPane().setPreferredSize(r.getSize());

        createTextPane();

        immediateColorization = new TImmediateColorization(textPane);
        autoIndent = new TAutoIndent(textPane);

        highlightCursorLine = EditorPreferences.getHighlightCursorEnabled();

        // Set by default the end of line property in order to always use the Unix style
        textPane.getDocument().putProperty(DefaultEditorKit.EndOfLineStringProperty, unixEndOfLine);
        textPane.setDelegate(this);

        gutter = new Gutter(textPane);

        textScrollPane = new JScrollPane(textPane);
        textScrollPane.setWheelScrollingEnabled(true);
        textScrollPane.setRowHeaderView(gutter);

        rulesTree = new XJTree() {
            public String getToolTipText(MouseEvent e) {
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if(path == null)
                    return "";

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Rules.RuleTreeUserObject n = (Rules.RuleTreeUserObject) node.getUserObject();
                if(n == null)
                    return "";

                Parser.Rule r = n.rule;
                if(r == null || !r.hasErrors())
                    return "";
                else
                    return r.getErrorMessageHTML();
            }
        };
        rulesTree.setBorder(null);
        // Apparently, if I don't set the tooltip here, nothing is displayed (weird)
        rulesTree.setToolTipText("");
        rulesTree.setDragEnabled(true);

        rulesScrollPane = new JScrollPane(rulesTree);
        rulesScrollPane.setBorder(null);
        rulesScrollPane.setPreferredSize(new Dimension(200, 0));
        rulesScrollPane.setWheelScrollingEnabled(true);

        // Assemble right text pane
        analysisStrip = new AnalysisStrip(editor);
        analysisStrip.setMinimumSize(new Dimension(18, 0));
        analysisStrip.setMaximumSize(new Dimension(18, Integer.MAX_VALUE));
        analysisStrip.setPreferredSize(new Dimension(18, analysisStrip.getPreferredSize().height));

        Box rightPaneBox = Box.createHorizontalBox();
        rightPaneBox.add(textScrollPane);
        rightPaneBox.add(analysisStrip);

        // Assemble

        viewTabbedPane = new JTabbedPane();
        viewTabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
        viewTabbedPane.addMouseListener(new TabMouseListener());

        rulesTextSplitPane = new JSplitPane();
        rulesTextSplitPane.setBorder(null);
        rulesTextSplitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        rulesTextSplitPane.setLeftComponent(rulesScrollPane);
        rulesTextSplitPane.setRightComponent(rightPaneBox);
        rulesTextSplitPane.setContinuousLayout(true);
        rulesTextSplitPane.setPreferredSize(new Dimension(0, 400));
        rulesTextSplitPane.setOneTouchExpandable(true);
        rulesTextSplitPane.setDividerLocation(0.3);

        upDownSplitPane = new JSplitPane();
        upDownSplitPane.setBorder(null);
        upDownSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        upDownSplitPane.add(rulesTextSplitPane, JSplitPane.TOP);
        upDownSplitPane.add(viewTabbedPane, JSplitPane.BOTTOM);
        upDownSplitPane.setContinuousLayout(true);
        upDownSplitPane.setOneTouchExpandable(true);

        infoLabel = new JLabel();
        cursorLabel = new JLabel();
        scmLabel = new JLabel();

        infoPanel = new InfoPanel();
        infoPanel.setPreferredSize(new Dimension(0, 30));

        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(infoLabel);
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(createSeparator());
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(cursorLabel);
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(createSeparator());
        infoPanel.add(Box.createHorizontalStrut(5));
        infoPanel.add(scmLabel);

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(upDownSplitPane, BorderLayout.CENTER);
        mainPanel.add(infoPanel, BorderLayout.SOUTH);

        editor.getContentPane().add(mainPanel);
        editor.pack();

        upDownSplitPane.setDividerLocation(0.5);

        XJNotificationCenter.defaultCenter().addObserver(this, DialogPrefs.NOTIF_PREFS_APPLIED);
    }

    public static JComponent createSeparator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        Dimension d = s.getMaximumSize();
        d.width = 2;
        s.setMaximumSize(d);
        return s;
    }

    public void close() {
        XJNotificationCenter.defaultCenter().removeObserver(this);
    }

    public void createTextPane() {
        textPane = new TextEditorPane(new EditorStyledDocument());
        textPane.setEditorKit(new CustomEditorKit());
        textPane.setBackground(Color.white);
        textPane.setBorder(null);

        applyFont();
        textPane.setWordWrap(false);

        textPaneListener = new TextPaneListener();
        textPane.getDocument().addDocumentListener(textPaneListener);

        textPane.addCaretListener(new TextPaneCaretListener());

        textPane.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent event) {
                if(event.getKeyCode() == KeyEvent.VK_C && (event.isMetaDown() || event.isControlDown())) {
                    editor.menuEditActions.performCopyToClipboard();
                    event.consume();
                }

                if(event.getKeyCode() == KeyEvent.VK_X && (event.isMetaDown() || event.isControlDown())) {
                    editor.menuEditActions.performCutToClipboard();
                    event.consume();
                }
            }
        });

        textPane.addMouseListener(new TextPaneMouseAdapter());
        textPane.addMouseMotionListener(new TextPaneMouseMotionAdapter());

        textPane.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                Dimension d = textPane.getSize();
                d.width = 20;
                gutter.setPreferredSize(d);
            }
        });
    }

    public void setAutoIndent(boolean flag) {
        autoIndent.setEnabled(flag);
    }

    public boolean autoIndent() {
        return autoIndent.enabled();
    }

    public void applyFont() {
        textPane.setFont(new Font(EditorPreferences.getEditorFont(), Font.PLAIN, EditorPreferences.getEditorFontSize()));
        applyTab();
    }

    public void applyTab() {
        int tabSize = EditorPreferences.getEditorTabSize();
        int charWidth = EditorPreferences.getEditorFontSize();
        try {
            charWidth = Toolkit.getDefaultToolkit().getFontMetrics(textPane.getFont()).stringWidth("m");
        } catch(Exception e) {
            // ignore exception
        }

        // @todo I have to create a fixed size array - any other way to do that ?
        TabStop[] tstops = new TabStop[100];
        for(int i = 0; i<100; i++) {
            tstops[i] = new TabStop(i*tabSize*charWidth);
        }
        TabSet tabs = new TabSet(tstops);

        Style style = textPane.getLogicalStyle();
        StyleConstants.setTabSet(style, tabs);
        textPane.setLogicalStyle(style);
    }

    public int getCaretPosition() {
        return textPane.getCaretPosition();
    }

    public int getCurrentLinePosition() {
        return editor.getLineIndexAtTextPosition(editor.getCaretPosition()) + 1;
    }

    public int getCurrentColumnPosition() {
        int lineIndex = editor.getLineIndexAtTextPosition(editor.getCaretPosition());
        Point linePosition = editor.getLineTextPositionsAtLineIndex(lineIndex);
        if(linePosition == null)
            return 1;
        else
            return editor.getCaretPosition() - linePosition.x + 1;
    }

    public int getSelectionStart() {
        return textPane.getSelectionStart();
    }

    public int getSelectionEnd() {
        return textPane.getSelectionEnd();
    }

    public void replaceSelectedText(String replace) {
        replaceText(getSelectionStart(), getSelectionEnd(), replace);
    }

    public void replaceText(int start, int end, String text) {
        try {
            textPane.getDocument().remove(start, end-start);
            textPane.getDocument().insertString(start, text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public void selectTextRange(int start, int end) {
        textPane.setCaretPosition(start);
        textPane.moveCaretPosition(end);
        textPane.getCaret().setSelectionVisible(true);

        Rectangle r;
        try {
            r = textPane.modelToView(start);
            textPane.scrollRectToVisible(r);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public void updateInformation() {
        String t;
        int size = 0;
        if(editor.parser.getRules() != null)
            size = editor.parser.getRules().size();
        switch(size) {
            case 0:
                t = "No rules";
                break;
            case 1:
                t = "One rule";
                break;
            default:
                t = size+" rules";
                break;
        }

        int warnings = editor.rules.getNumberOfRulesWithErrors();
        if(warnings > 0)
            t += " ("+warnings+" warnings)";

        infoLabel.setText(t);
    }

    public void updateCursorInfo() {
        cursorLabel.setText(getCurrentLinePosition()+":"+getCurrentColumnPosition());
    }

    public void updateSCMStatus(String status) {
        scmLabel.setVisible(EditorPreferences.getP4Enabled());
        if(status != null)
            scmLabel.setText("SCM Status: "+status);
        else
            scmLabel.setText("");
    }

    public void registerUndo(Undo undo, JComponent component) {
        component.addFocusListener(new EditorFocusListener());
    }

    public void updateUndoRedo(Object source) {
        Undo undo = editor.getUndo(source);
        updateUndoRedo(undo);
    }

    public void updateUndoRedo(Undo undo) {
        if(editor.editorMenu == null || editor.editorMenu.menuItemUndo == null
                || editor.editorMenu.menuItemRedo == null)
            return;

        editor.editorMenu.menuItemUndo.setTitle("Undo");
        editor.editorMenu.menuItemRedo.setTitle("Redo");

        if(undo == null) {
            editor.editorMenu.menuItemUndo.setEnabled(false);
            editor.editorMenu.menuItemRedo.setEnabled(false);
        } else {
            editor.editorMenu.menuItemUndo.setEnabled(undo.canUndo());
            editor.editorMenu.menuItemRedo.setEnabled(undo.canRedo());

            if(undo.canUndo())
                editor.editorMenu.menuItemUndo.setTitle("Undo "+undo.undoManager.getPresentationName());
            if(undo.canRedo())
                editor.editorMenu.menuItemRedo.setTitle("Redo "+undo.undoManager.getPresentationName());
        }
    }

    public void undoStateDidChange(Undo undo) {
        updateUndoRedo(undo);
    }

    public void notificationFire(Object source, String name) {
        if(name.equals(DialogPrefs.NOTIF_PREFS_APPLIED)) {
            highlightCursorLine = EditorPreferences.getHighlightCursorEnabled();
            applyFont();
            textScrollPane.repaint();
            editor.getMainMenuBar().refreshState();
            textPane.repaint();
            updateSCMStatus(null);
        }
    }

    public void makeBottomComponentVisible() {
        if(upDownSplitPane.getBottomComponent().getHeight() == 0) {
            upDownSplitPane.setDividerLocation(upDownSplitPane.getLastDividerLocation());
        }
    }

    public void textEditorPaneDidPaint(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        if(editor.getTokens() == null)
            return;

        for (Iterator iterator = editor.getTokens().iterator(); iterator.hasNext();) {
            Token token = (Token) iterator.next();

            if(token.type != Lexer.TOKEN_ID)
                continue;

            if(editor.rules.isUndefinedToken(token)) {
                g.setColor(Color.red);
                drawUnderlineAtIndexes(g, token.getStartIndex(), token.getEndIndex());
            }

            if(editor.rules.isDuplicateRule(token.getAttribute())) {
                g.setColor(Color.blue);
                drawUnderlineAtIndexes(g, token.getStartIndex(), token.getEndIndex());
            }

            Parser.Rule rule = editor.rules.getRuleStartingWithToken(token);
            if(rule != null && rule.hasLeftRecursion()) {
                g.setColor(Color.green);
                drawUnderlineAtIndexes(g, token.getStartIndex(), token.getEndIndex());
            }
        }
    }

    public class CustomEditorKit extends StyledEditorKit implements ViewFactory {

        // This class has been inspired by fabrice_pi at this URL:
        // http://www.javafr.com/code.aspx?ID=21900

        public ViewFactory getViewFactory() {
            return this;
        }

        public Document createDefaultDocument() {
            return new EditorStyledDocument();
        }

        public View create(Element elem) {
            String kind = elem.getName();

            if(AbstractDocument.ParagraphElementName.equals(kind))
                return new LineHighlightingParagraphView(elem);
            else
                return super.getViewFactory().create(elem);
        }
    }

    public class LineHighlightingParagraphView extends ParagraphView {
        public Rectangle tempRect = new Rectangle();
        public Color highlightColor = new Color(1.0f, 1.0f, 0.5f, 0.3f);

        public LineHighlightingParagraphView(Element elem) {
            super(elem);
        }

        public void paint(Graphics g, Shape allocation) {
            if(highlightCursorLine) {
                Rectangle alloc = (allocation instanceof Rectangle) ?
                        (Rectangle)allocation :
                        allocation.getBounds();
                int n = getViewCount();
                int x = alloc.x + getLeftInset();
                int y = alloc.y + getTopInset();
                Rectangle clip = g.getClipBounds();
                int cursorPosition = textPane.getCaretPosition()+1;
                for (int i = 0; i < n; i++) {
                    tempRect.x = x + getOffset(X_AXIS, i);
                    tempRect.y = y + getOffset(Y_AXIS, i);
                    tempRect.width = getSpan(X_AXIS, i);
                    tempRect.height = getSpan(Y_AXIS, i);
                    if (tempRect.intersects(clip)) {
                        View v = getView(i);

                        if (v.getStartOffset() < cursorPosition &&
                                cursorPosition <= v.getEndOffset()) {
                            g.setColor(highlightColor);
                            g.fillRect(tempRect.x, tempRect.y,
                                    alloc.width, tempRect.height);
                        }

                        paintChild(g, tempRect, i);
                    }
                }
            } else {
                super.paint(g, allocation);
            }
        }
    }

    public void drawUnderlineAtIndexes(Graphics g, int start, int end) {
        try {
            Rectangle r1 = textPane.modelToView(start);
            Rectangle r2 = textPane.modelToView(end);

            int width = r2.x-r1.x;
            int triangle_size = 5;
            for(int triangle=0; triangle<width/triangle_size; triangle++) {
                int x = r1.x+triangle*triangle_size;
                int y = r1.y+r1.height-1;
                g.drawLine(x, y, x+triangle_size/2, y-triangle_size/2);
                g.drawLine(x+triangle_size/2, y-triangle_size/2, x+triangle_size, y);
            }
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    protected class TabMouseListener extends MouseAdapter {

        protected static final int CLOSING_INDEX_LIMIT = 4;

        public void mousePressed(MouseEvent event) {
            if(viewTabbedPane.getSelectedIndex() < CLOSING_INDEX_LIMIT)
                return;

            if(event.isPopupTrigger()) {
                JPopupMenu popup = new JPopupMenu();
                JMenuItem item = new JMenuItem("Close");
                item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        if(viewTabbedPane.getSelectedIndex() < CLOSING_INDEX_LIMIT)
                            return;

                        viewTabbedPane.removeTabAt(viewTabbedPane.getSelectedIndex());
                    }
                });
                popup.add(item);
                popup.show(event.getComponent(), event.getX(), event.getY());
            }
        }
    }

    protected class InfoPanel extends Box {

        public InfoPanel() {
            super(BoxLayout.X_AXIS);
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            Rectangle r = getBounds();

            g.setColor(Color.darkGray);
            g.drawLine(0, 0, r.width, 0);

            g.setColor(Color.lightGray);
            g.drawLine(0, 1, r.width, 1);
        }
    }

    protected class TextPaneCaretListener implements CaretListener {

        public void caretUpdate(CaretEvent e) {
            updateCursorInfo();

            // Each time the cursor moves, update the visible part of the text pane
            // to redraw the highlighting
            if(highlightCursorLine)
                editor.getTextPane().repaint();

            // Update the auto-completion list
            editor.autoCompletionMenu.updateAutoCompleteList();

            // Only display ideas using the mouse because otherwise when a rule
            // is deleted (for example), the idea might be displayed before
            // the parser was able to complete
            //displayIdeas(e.getDot());

            Parser.Rule rule = editor.rules.selectRuleAtPosition(e.getDot());
            if(rule == null || rule.name == null)
                return;

            if(lastSelectedRule == null || !lastSelectedRule.equals(rule.name)) {
                lastSelectedRule = rule.name;
                editor.updateVisualization(false);
            }
        }
    }

    protected class TextPaneListener implements DocumentListener {

        protected int enable = 0;

        public synchronized void enable() {
            enable--;
        }

        public synchronized void disable() {
            enable++;
        }

        public synchronized boolean isEnable() {
            return enable == 0;
        }

        public void changeUpdate(int offset, int length) {
            editor.changeUpdate(offset, length);
        }

        public void insertUpdate(DocumentEvent e) {
            if(!isEnable())
                return;

            immediateColorization.colorize(e.getOffset(), e.getLength());
            autoIndent.indent(e.getOffset(), e.getLength());

            changeUpdate(e.getOffset(), e.getLength());
        }

        public void removeUpdate(DocumentEvent e) {
            if(!isEnable())
                return;

            changeUpdate(e.getOffset(), -e.getLength());
        }

        public void changedUpdate(DocumentEvent e) {
        }
    }

    protected class EditorFocusListener implements FocusListener {

        public void focusGained(FocusEvent event) {
            updateUndoRedo(event.getSource());
        }

        public void focusLost(FocusEvent event) {
            // Update the menu only if the event is not temporary. Temporary
            // focus lost can be, for example, when opening a menu on Windows/Linux.
            if(!event.isTemporary())
                updateUndoRedo(null);
        }
    }

    protected class TextPaneMouseAdapter extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            // Update the cursor highligthing
            if(highlightCursorLine)
                textPane.repaint();

            editor.displayIdeas(e.getPoint());
        }
    }

    protected class TextPaneMouseMotionAdapter extends MouseMotionAdapter {
        public void mouseMoved(MouseEvent e) {
            if(textPane.hasFocus()) {
                Point relativePoint = e.getPoint();
                Point absolutePoint = SwingUtilities.convertPoint(textPane, relativePoint, editor.getJavaContainer());
                editor.displayTips(relativePoint, absolutePoint);                
            }
        }
    }

}