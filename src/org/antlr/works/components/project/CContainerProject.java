package org.antlr.works.components.project;

import edu.usfca.xj.appkit.frame.XJDialog;
import edu.usfca.xj.appkit.frame.XJFrame;
import edu.usfca.xj.appkit.frame.XJWindow;
import edu.usfca.xj.appkit.menu.XJMainMenuBar;
import edu.usfca.xj.appkit.swing.XJTree;
import edu.usfca.xj.appkit.utils.XJAlert;
import edu.usfca.xj.foundation.XJUtils;
import org.antlr.works.components.ComponentContainer;
import org.antlr.works.components.ComponentEditor;
import org.antlr.works.components.project.file.CContainerProjectFile;
import org.antlr.works.components.project.file.CContainerProjectGrammar;
import org.antlr.works.components.project.file.CContainerProjectJava;
import org.antlr.works.components.project.file.CContainerProjectText;
import org.antlr.works.project.*;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

public class CContainerProject extends XJWindow implements ComponentContainer {

    protected XJMainMenuBar projectDefaultMainMenuBar;
    protected ProjectToolbar toolbar;
    protected JSplitPane splitPaneA;
    protected JSplitPane splitPaneB;

    protected JPanel projectPanel;
    protected XJTree filesTree;
    protected DefaultMutableTreeNode filesTreeRootNode;
    protected DefaultTreeModel filesTreeModel;

    protected ComponentContainer currentFileContainer;

    protected ProjectBuilder builder;
    protected ProjectConsole console;

    public static final String FILE_GRAMMAR_EXTENSION = ".g";
    public static final String FILE_JAVA_EXTENSION = ".java";

    public static final String FILE_TYPE_GRAMMAR = "FILE_TYPE_GRAMMAR";
    public static final String FILE_TYPE_JAVA = "FILE_TYPE_JAVA";
    public static final String FILE_TYPE_TEXT = "FILE_TYPE_TEXT";

    public static String getFileType(String filePath) {
        if(filePath.endsWith(FILE_GRAMMAR_EXTENSION))
            return FILE_TYPE_GRAMMAR;
        if(filePath.endsWith(FILE_JAVA_EXTENSION))
            return FILE_TYPE_JAVA;

        return FILE_TYPE_TEXT;
    }

    public CContainerProject() {
        builder = new ProjectBuilder(this);
        console = new ProjectConsole();

        projectPanel = new JPanel(new BorderLayout());

        toolbar = new ProjectToolbar(this);
        projectPanel.add(toolbar.getToolbar(), BorderLayout.NORTH);

        splitPaneA = new JSplitPane();
        splitPaneA.setBorder(null);
        splitPaneA.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        splitPaneA.setLeftComponent(createFilesTree());
        splitPaneA.setRightComponent(currentEditorPanel());
        splitPaneA.setContinuousLayout(true);
        splitPaneA.setOneTouchExpandable(true);
        splitPaneA.setDividerLocation(150);

        splitPaneB = new JSplitPane();
        splitPaneB.setBorder(null);
        splitPaneB.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPaneB.setLeftComponent(splitPaneA);
        splitPaneB.setRightComponent(console.getContainer());
        splitPaneB.setContinuousLayout(true);
        splitPaneB.setOneTouchExpandable(true);
        splitPaneB.setDividerLocation(getRootPane().getPreferredSize().height);

        projectPanel.add(splitPaneB, BorderLayout.CENTER);

        getContentPane().add(projectPanel);

        getJFrame().pack();
    }

    public void awake() {
        super.awake();
        getData().setProject(this);
        projectDefaultMainMenuBar = mainMenuBar;
    }

    public void setDefaultSize() {
        Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        r.height *= 0.75;
        r.width *= 0.8;
        getRootPane().setPreferredSize(r.getSize());
    }

    public void setDefaultMainMenuBar() {
        setMainMenuBar(projectDefaultMainMenuBar);
    }

    public void setTitle(String title) {
        super.setTitle(title+" - [Project]");
    }

    public ProjectData getData() {
        return ((CDocumentProject)getDocument()).data;
    }

    public ProjectBuildList getBuildList() {
        return getData().getBuildList();
    }

    public String getProjectFolder() {
        return XJUtils.getPathByDeletingLastComponent(getDocument().getDocumentPath());
    }

    public JComponent createFilesTree() {

        filesTree = new XJTree() {
            public String getToolTipText(MouseEvent e) {
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if(path == null)
                    return "";

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                ProjectFileItem item = (ProjectFileItem) node.getUserObject();
                if(item == null)
                    return "";

                return item.getFilePath();
            }
        };

        filesTree.setBorder(null);
        // Apparently, if I don't set the tooltip here, nothing is displayed (weird)
        filesTree.setToolTipText("");
        filesTree.setDragEnabled(true);
        filesTree.setRootVisible(false);
        filesTree.setShowsRootHandles(true);
//        filesTree.setEnableDragAndDrop();

        filesTreeRootNode = new DefaultMutableTreeNode();
        filesTreeModel = new DefaultTreeModel(filesTreeRootNode);

        filesTree.setModel(filesTreeModel);
        filesTree.addTreeSelectionListener(new RuleTreeSelectionListener());

        JScrollPane scrollPane = new JScrollPane(filesTree);
        scrollPane.setWheelScrollingEnabled(true);
        return scrollPane;
    }

    public JPanel createInfoPanel(String info) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(info);
        l.setHorizontalAlignment(JLabel.CENTER);
        l.setFont(new Font("dialog", Font.PLAIN, 36));
        l.setForeground(Color.gray);
        p.add(l, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.lightGray));
        return p;
    }

    public JPanel loadingEditorPanel() {
        return createInfoPanel("Loading...");
    }

    public JPanel noEditorPanel() {
        return createInfoPanel("No Selected File");
    }

    public JPanel currentEditorPanel() {
        if(currentFileContainer == null)
            return noEditorPanel();
        else
            return currentFileContainer.getEditor().getPanel();
    }

    public void setEditorZonePanel(JPanel panel) {
        int loc = splitPaneA.getDividerLocation();
        splitPaneA.setRightComponent(panel);
        splitPaneA.setDividerLocation(loc);
        splitPaneA.repaint();
    }

    public void makeBottomComponentVisible() {
        if(splitPaneB.getBottomComponent().getHeight() == 0) {
            splitPaneB.setDividerLocation(0.5);
        }
    }

    public XJFrame getXJFrame() {
        return this;
    }

    public void windowActivated() {
        super.windowActivated();

        if(getBuildList().handleExternalModification())
            changeDone();

        runClosureOnFileEditorItems(new FileEditorItemClosure() {
            public void process(ProjectFileItem item) {
                if(item.handleExternalModification())
                    changeDone();
                item.windowActivated();
            }
        });
    }

    public void windowDocumentPathDidChange() {
        // Called when the document associated file has changed on the disk
        // Not used because we don't allow external modification of the project file
    }

    /** Project handling methods
     *
     */

    public void changeDone() {
        getDocument().changeDone();
    }

    public void setCurrentFileEditor(ProjectFileItem item) {
        if(item == null) {
            currentFileContainer = null;
            setMainMenuBar(projectDefaultMainMenuBar);
            setEditorZonePanel(currentEditorPanel());
        } else {
            currentFileContainer = item.getComponentContainer();
            if(currentFileContainer == null) {
                setEditorZonePanel(loadingEditorPanel());

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        currentFileContainer = new ProjectFileItemFactory(getSelectedFileEditorItem()).create();
                    }
                });
            } else {
                fileEditorItemDidLoad(item);
            }
        }
    }

    public void fileEditorItemDidLoad(ProjectFileItem item) {
        setMainMenuBar(item.getComponentContainer().getMainMenuBar());
        setEditorZonePanel(currentEditorPanel());
        if(currentFileContainer != null)
            currentFileContainer.getEditor().componentIsSelected();
    }

    public List getFileEditorItems() {
        List items = new ArrayList();

        for(int index=0; index<filesTreeRootNode.getChildCount(); index++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) filesTreeRootNode.getChildAt(index);
            ProjectFileItem item = (ProjectFileItem) node.getUserObject();
            items.add(item);
        }

        return items;
    }

    public String[] getRunParameters() {
        String s = getData().getRunParametersString();
        if(s == null)
            return null;
        else
            return s.split(" ");
    }

    public void settings(boolean runAfterSettings) {
        ProjectRunSettingsDialog dialog = new ProjectRunSettingsDialog(getJavaContainer());
        dialog.setRunParametersString(getData().getRunParametersString());
        dialog.setShowBeforeRunning(getData().getShowBeforeRunning());
        if(dialog.runModal() == XJDialog.BUTTON_OK) {
            changeDone();
            getData().setRunParametersString(dialog.getRunParametersString());
            getData().setShowBeforeRunning(dialog.isShowBeforeRunning());
            if(runAfterSettings) {
                buildAndRun();
            }
        }
    }

    public void run() {
        String[] params = getRunParameters();
        if(params == null || params.length == 0 || getData().getShowBeforeRunning())
            settings(true);
        else
            buildAndRun();
    }

    public void clean() {
        builder.clean();
    }

    public void build() {
        builder.build();
    }

    public void buildAndRun() {
        builder.run();
    }

    public void buildReportError(String error) {
        printToConsole(error);
    }

    public void clearConsole() {
        console.clear();
    }

    public void printToConsole(String s) {
        console.print(s);
        makeBottomComponentVisible();
    }

    public void printToConsole(Exception e) {
        printToConsole(XJUtils.stackTrace(e));
    }

    public void changeCurrentEditor() {
        setCurrentFileEditor(getSelectedFileEditorItem());
    }

    public void close() {
        runClosureOnFileEditorItems(new FileEditorItemClosure() {
            public void process(ProjectFileItem item) {
                item.close();
            }
        });

        super.close();
    }

    /** Management of project's files
     *
     */

    public void addFilePath(String filePath) {
        ProjectFileItem item = getData().addProjectFile(filePath);
        getBuildList().addFile(item.getFilePath(), item.getFileType());
        filesTreeRootNode.add(new DefaultMutableTreeNode(item));
    }

    public void addFilePaths(List filePaths) {
        for (Iterator iterator = filePaths.iterator(); iterator.hasNext();) {
            String filePath = (String) iterator.next();
            addFilePath(filePath);
        }
        filesTreeModel.reload();
        changeDone();
    }

    public void removeSelectedFiles() {
        TreePath selPath[] = filesTree.getSelectionPaths();
        if(selPath == null || selPath.length < 1)
            return;

        boolean removed = false;
        boolean cancel = false;
        for (int i = 0; i < selPath.length && !cancel; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)selPath[i].getLastPathComponent();
            ProjectFileItem item = (ProjectFileItem)node.getUserObject();
            if(item.isDirty()) {
                int result = (XJAlert.displayAlertYESNOCANCEL(getJavaContainer(), "Save Content", "Do you want to save the content of "+item.getFileName()+" before removing it from the project ?"));
                switch(result) {
                    case XJAlert.CANCEL:
                        cancel = true;
                        continue;
                    case XJAlert.YES:
                        item.save();
                        break;
                    case XJAlert.NO:
                        break;
                }
            }

            item.close();
            getBuildList().removeFile(item.getFilePath(), item.getFileType());
            getData().removeProjectFile(item);
            filesTreeRootNode.remove(node);
            removed = true;
        }

        if(removed) {
            currentFileContainer = null;
            filesTreeModel.reload();
            changeDone();
        }
    }

    public ProjectFileItem getSelectedFileEditorItem() {
        TreePath selPath[] = filesTree.getSelectionPaths();
        if(selPath == null || selPath.length < 1)
            return null;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selPath[0].getLastPathComponent();
        return (ProjectFileItem)node.getUserObject();
    }

    /** This method is called *very* frequently so it has to be really efficient
     *
     */

    public void fileDidBecomeDirty(CContainerProjectFile file, ProjectFileItem item) {
        getBuildList().setFileDirty(item.getFilePath(), item.getFileType(), true);
        changeDone();
    }

    public void documentDidLoad() {
        for (Iterator iterator = getData().getProjectFiles().iterator(); iterator.hasNext();) {
            ProjectFileItem item = (ProjectFileItem) iterator.next();
            filesTreeRootNode.add(new DefaultMutableTreeNode(item));
        }
        filesTree.setSelectionRow(0);
        filesTreeModel.reload();
    }

    public void documentWillSave() {
        runClosureOnFileEditorItems(new FileEditorItemClosure() {
            public void process(ProjectFileItem item) {
                ComponentContainer container = item.getComponentContainer();
                if(container != null)
                    container.getDocument().performSave(false);
            }
        });
    }

    public interface FileEditorItemClosure {
        public void process(ProjectFileItem item);
    }

    public void runClosureOnFileEditorItems(FileEditorItemClosure closure) {
        for(int index=0; index<filesTreeRootNode.getChildCount(); index++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) filesTreeRootNode.getChildAt(index);
            ProjectFileItem item = (ProjectFileItem) node.getUserObject();
            closure.process(item);
        }
    }

    public void loadText(String text) {
        // Not used for project
    }

    public String getText() {
        // Not used for project
        return null;
    }

    public boolean willSaveDocument() {
        // Not used for project
        return true;
    }

    public ComponentEditor getEditor() {
        // There is no specific editor for the project
        return null;
    }

    protected class ProjectFileItemFactory {

        public ProjectFileItem item;

        public ProjectFileItemFactory(ProjectFileItem item) {
            this.item = item;
        }

        public ComponentContainer create() {

            String type = getFileType(item.getFilePath());
            if(type.equals(FILE_TYPE_GRAMMAR))
                new CContainerProjectGrammar(CContainerProject.this, item);
            else if(type.equals(FILE_TYPE_JAVA))
                new CContainerProjectJava(CContainerProject.this, item);
            else
                new CContainerProjectText(CContainerProject.this, item);

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    item.getComponentContainer().getDocument().performLoad(item.getFilePath());
                    fileEditorItemDidLoad(item);
                }
            });

            return item.getComponentContainer();
        }

    }

    protected class RuleTreeSelectionListener implements TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent e) {
            changeCurrentEditor();
        }
    }

}
