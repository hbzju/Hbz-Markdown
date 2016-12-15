/**
 * Created by wanghaobo on 16/12/1.
 */

/**
 * requirement:
 *      we need a markdown editor, with enhanced feature listed below:
 *   1.A left side panel shows the structure of the document. Structure is defined as <h?>.
 * An click on the item in the structure panel brings that <h?> at the first line in the editor panel.
 *   2.It is able to deal with large document, up to 1MB bytes.   ####
 *   3.HTML tags are recognizable.  ###
 *   4.User can specify his own .css to generate the HTML output.
 *   5.It is able to generate .docx file.   ####
 */

import org.docx4j.model.styles.Tree;
import org.markdown4j.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class TreeNodeUserData{
    private String nodeName;
    private String nodeContent;
    private int index;

    TreeNodeUserData(String name,String content,int headerCount){
        nodeName=name;
        nodeContent=content;
        index=headerCount;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getNodeContent() {
        return nodeContent;
    }

    public int getIndex() {
        return index;
    }

    public String toString(){
        if (nodeName=="DOC"){
            return "文档";
        }
        else if (nodeContent.equals("")){
            return getNodeName()+"(标题未定义)";
        }
        else if (nodeContent.length()<=8){
            return getNodeContent();
        }
        else {
            return getNodeContent().substring(0,7)+"…";
        }
    }
}
//Dom树的结点类

class myTreeRender extends DefaultTreeCellRenderer{
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                  boolean sel, boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus)
    {

        //执行父类原型操作
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,
                row, hasFocus);
//
//        setText(value.toString());

        if (sel)
        {
            setForeground(getTextSelectionColor());
        }
        else
        {
            setForeground(getTextNonSelectionColor());
        }

        //得到每个节点的TreeNode
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;

        TreeNodeUserData data = (TreeNodeUserData) node.getUserObject();
        String str = data.getNodeName();

        //判断是哪个文本的节点设置对应的值（这里如果节点传入的是一个实体,则可以根据实体里面的一个类型属性来显示对应的图标）
        if (str == "DOC")
        {
            ImageIcon icon = new ImageIcon(this.getClass().getResource("root.png"));
            icon.setImage(icon.getImage().getScaledInstance(13,13,Image.SCALE_DEFAULT));
            this.setIcon(icon);
        }
        else {
            ImageIcon icon = new ImageIcon(this.getClass().getResource("doc.png"));
            icon.setImage(icon.getImage().getScaledInstance(13,13,Image.SCALE_DEFAULT));
            this.setIcon(icon);
        }

        return this;
    }
}

public class myMarkdown extends JFrame {

    private JEditorPane mdShow;
    private JTextArea textAreaInput;
    private JTextArea blank1,blank2;
    private JScrollPane editScroll;
    private JScrollPane mdScroll;
    private JTree domTree;
    private JMenuBar menuBar;
    private JLabel status;
    private JDialog cssDialog;

    private int headerCount;
    private String mdhtml;
    private String mdStr;
    private String css;
    private String readmeTip;
    final private HtmlConverter converter;
    private DefaultMutableTreeNode root;
    private Markdown4jProcessor parser=new Markdown4jProcessor();

    private File file;
    private File tempFile;
    private String tempFilePath;

    {
        readmeTip = "1.本Markdown编辑器主要实现了：\n" +
                "\t\t\t\ta.实时预览;\n" +
                "\t\t\t\tb.文档结构的显示与定位;\n" +
                "\t\t\t\tc.外部样式定义;\n" +
                "\t\t\t\td.HTML和DOCX的导出;\n" +
                "\t\t\t\te.文件的打开与保存;\n" +
                "\t\t\t\td.HTML标签的解析;\n" +
                "2.HTML和DOCX文件的导出在菜单栏文件->导出中。\n" +
                "3.CSS的样式被认为非标准功能，需要从菜单栏编辑->样式中自行输入。\n" +
                "4.可以直接在菜单栏打开一个文件，将文件内容放在输入框中，也可以保存.md文件。\n" +
                "5.可以在编辑框中输入Markdown或HTML的语法。\n" +
                "6.如果HTML标签使用时存在<h?>标签的嵌套使用，将会显示树状的文件结构，否则均认为是同级标签。\n" +
                "7.点击仅显示<h?>标签的DOM树的某个结点，能够跳转到对应的行，如果使用了某些HTML语法，可能会导致右侧跳转不灵敏。\n" +
                "8.支持src属性,这意味着您可以引入自定义的CSS文件、图片等等,但由于这需要进行刷新,在体验上可能带来一些问题。\n" +
                "\t\t\t\t更加详细的说明请见README文件。\n";
        tempFilePath = "file://"+Class.class.getClass().getResource("/").getPath()+"temp.html";
    }

    public myMarkdown() {
        //初始化
        converter = new HtmlConverter();
        css="";
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        file = null;
        tempFile = new File(Class.class.getClass().getResource("/").getPath(),"temp.html");
        if (!tempFile.exists()){
            try {
                tempFile.createNewFile();
            }catch (Exception e){
                e.printStackTrace();
                System.exit(1);
            }
        }

        //输入区域
        textAreaInput=new JTextArea();
        textAreaInput.setFont(font);
        textAreaInput.setLineWrap(true);
        textAreaInput.setWrapStyleWord(true);
        textAreaInput.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                changed();
            }
        });

        //空白区域
        blank1 = new JTextArea();
        blank1.setFocusable(false);
        blank1.setEditable(false);
        blank1.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                textAreaInput.requestFocus();
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        blank1.setBackground(Color.white);
        JPanel editPane=new JPanel();
        editPane.setLayout(new BorderLayout());
        editPane.add(textAreaInput);
        editPane.add(blank1,BorderLayout.SOUTH);

        //编辑区滚动条
        editScroll = new JScrollPane(editPane);
        editScroll.setPreferredSize(new Dimension(600,600));
        editScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        editScroll.setRowHeaderView(new LineNumberHeaderView());

        //Markdown显示区
        mdShow = new JEditorPane();
        HTMLEditorKit kit = new HTMLEditorKit();
        Document doc = kit.createDefaultDocument();
        //初始化样式表
//        style = kit.getStyleSheet();
        mdShow.setEditorKit(kit);
        mdShow.setDocument(doc);
//        mdShow.setPreferredSize(new Dimension(800,700));
        mdShow.setEditable(false);
        mdShow.setFont(font);

        blank2 = new JTextArea();
        blank2.setFocusable(false);
        blank2.setEditable(false);
        blank2.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                textAreaInput.requestFocus();
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        blank2.setBackground(Color.white);

        JPanel mdPanel = new JPanel();
        mdPanel.setLayout(new BorderLayout());
        mdPanel.add(mdShow);
        mdPanel.add(blank2,BorderLayout.SOUTH);
        mdScroll=new JScrollPane(mdPanel);
        mdScroll.setPreferredSize(new Dimension(600,600));
        mdScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        //文档Dom树显示区
        domTree=new JTree();
        domTree.setPreferredSize(new Dimension(100,0));
        domTree.setCellRenderer(new myTreeRender());
        setNodeListener();
        getDomTree("<DOC></DOC>");

        JPanel rightPane = new JPanel(new GridLayout(1,2));
        rightPane.add(editScroll);
        rightPane.add(mdScroll);

        //菜单栏
        menuBar = new JMenuBar();
        setMenuItems();
        setJMenuBar(menuBar);

        //提示框
        status = new JLabel();
        status.setPreferredSize(new Dimension(0,20));

        //添加组件
        add(rightPane);
        add(domTree,BorderLayout.WEST);
        add(status,BorderLayout.SOUTH);

        blank1.setPreferredSize(new Dimension(editScroll.getHeight()-textAreaInput.getFont().getSize(),598));
        blank2.setPreferredSize(new Dimension(editScroll.getHeight()-textAreaInput.getFont().getSize(),598));

        setSize(800,620);
        setLocation(50,50);
//        setExtendedState(Frame.MAXIMIZED_BOTH);
        setVisible(true);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setTitle("不方便透露姓名的Markdown编辑器");

        setCssDialog();
    }

    private void changed()
    {
        mdStr=textAreaInput.getText().toString();
        try{
            String[] lines=mdStr.split("\n");
            mdStr="";
            for (int i=0;i<lines.length;i++){
                if (lines[i].equals("<")){
                    lines[i]="&lt;";
                }
                mdStr=mdStr+lines[i]+"\n";
            }
            mdhtml = parser.process(mdStr);
        }catch (IOException e){
            mdhtml=mdStr;
        }finally {
            addCssStyle();
            if (mdhtml.contains("src=")) {
                //如果mdhtml中不存在src或者href,则没有图片和超链接
                try {
                    saveTempFile();
                    mdShow.setDocument(new javax.swing.text.html.HTMLDocument());
                    mdShow.setPage(tempFilePath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                mdShow.setText(mdhtml);
            }
            getDomTree("<DOC>"+mdhtml+"</DOC>");
            status.setText("");
        }
    }

    private void saveTempFile(){
        FileOutputStream fous = null;
        try {
            fous = new FileOutputStream(tempFile);//写入到这个目录中
            fous.write(mdhtml.getBytes());
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                fous.close();
            } catch (IOException ex) {
//                ex.printStackTrace();
            }
        }
    }

    private void getDomTree(String content){
        try{
            //静态方法newInstance得到解析工厂实例
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            //用解析工厂的newDocumentBuilder()方法得到解析对象
            DocumentBuilder builder=factory.newDocumentBuilder();
            org.w3c.dom.Document document=builder.parse(new ByteArrayInputStream(content.getBytes()));
            //调用遍历节点方法，参数为根节点
            headerCount=-1;
            root=traverseDom(document.getDocumentElement());
            domTree.setModel(new TreeModel() {
                @Override
                public Object getRoot() {
                    return root;
                }

                @Override
                public Object getChild(Object parent, int index) {
                    return ((DefaultMutableTreeNode)parent).getChildAt(index);
                }

                @Override
                public int getChildCount(Object parent) {
                    return ((DefaultMutableTreeNode)parent).getChildCount();
                }

                @Override
                public boolean isLeaf(Object node) {
                    return ((DefaultMutableTreeNode)node).isLeaf();
                }

                @Override
                public void valueForPathChanged(TreePath path, Object newValue) {

                }

                @Override
                public int getIndexOfChild(Object parent, Object child) {
                    return ((DefaultMutableTreeNode)parent).getIndex((DefaultMutableTreeNode)child);
                }

                @Override
                public void addTreeModelListener(TreeModelListener l) {

                }

                @Override
                public void removeTreeModelListener(TreeModelListener l) {

                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private DefaultMutableTreeNode traverseDom(Node node)
    {
        DefaultMutableTreeNode subroot=null;
        String rootName=node.getNodeName();

        if (node.getNodeType()==Node.ELEMENT_NODE&&node.getNodeName().matches("h[1-6]|DOC")){
            headerCount++;
            subroot=new DefaultMutableTreeNode(rootName.equals("DOC")?rootName:"<"+rootName+">");
            subroot.setUserObject(new TreeNodeUserData(node.getNodeName(),node.getTextContent(),headerCount));
            NodeList allNodes=node.getChildNodes();
            for (int i=0;i<allNodes.getLength();i++){
                Node child=allNodes.item(i);
                DefaultMutableTreeNode tchild=traverseDom(child);
                if (tchild!=null)subroot.add(tchild);
            }
        }
        return subroot;
    }

    private void setNodeListener(){
        domTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                int lineNumber=0;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                TreeNodeUserData userData=(TreeNodeUserData) node.getUserObject();
                if (userData.getNodeName().equals("DOC")){
                    textAreaInput.requestFocus();
                    textAreaInput.setCaretPosition(0);
                    editScroll.getVerticalScrollBar().setValue(0);
                    return;
                }
                String content=userData.getNodeContent();
//                System.out.println(content);
                if (!node.isLeaf()||(content.equals("")||isNotOnceTime(mdStr,content))){
                    //如果content不是唯一的,甚至是空字符串,那么定位会存在不准确的可能,因为用户自定义的<h?>标签可能存在错误
                    lineNumber=getLineNumberNotUnique(mdStr,userData.getIndex());
                }
                else if (node.isLeaf()){
                    //如果content是唯一的,那么定位非常准确
                    int position = mdStr.indexOf(content);
                    lineNumber=getLineNumberUnique(lineNumber,mdStr,position);
                }
                String[] inputContent=textAreaInput.getText().toString().split("\n");
                int cursorPosition=0;
                for (int i=0;i<lineNumber;i++){
                    cursorPosition+=inputContent[i].length()+1;
                }
//                System.out.println(lineNumber+" "+cursorPosition);
                try{
                    textAreaInput.requestFocus();
                    textAreaInput.setCaretPosition(cursorPosition);
                    Rectangle r =textAreaInput.modelToView(textAreaInput.getCaretPosition());
                    editScroll.getVerticalScrollBar().setValue((int)r.getY());
                }
                catch (Exception ex){
                    ex.printStackTrace();
                }

                try{
                    String editStr = mdStr.substring(0,cursorPosition);
                    String htmlStr = "";
                    try{
                        String[] lines=editStr.split("\n");
                        editStr="";
                        for (int i=0;i<lines.length;i++){
                            if (lines[i].equals("<")){
                                lines[i]="&lt;";
                            }
                            editStr=editStr+lines[i]+"\n";
                        }
                        htmlStr = parser.process(editStr);
                    }catch (IOException ioe){
                        htmlStr = editStr;
                        ioe.printStackTrace();
                    }
                    htmlStr=htmlStr.replaceAll("<([\\s\\S]*?)>|<([\\s\\S]*?)/>","");
                    mdShow.setCaretPosition(htmlStr.length()+1);
                    Rectangle r =mdShow.modelToView(mdShow.getCaretPosition());
                    mdScroll.getVerticalScrollBar().setValue((int)r.getY());
                }
                catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        });
    }

    private boolean isNotOnceTime(String str,String sub){
        return str.substring(str.indexOf(sub)+sub.length()).contains(sub);
    }

    private int getLineNumberUnique(int lineNumber,String str,int end){
        if (str.contains("\n")&&str.indexOf("\n")<end){
            lineNumber++;
            return getLineNumberUnique(lineNumber,str.substring(str.indexOf("\n")+1),end-str.indexOf("\n")-1);
        }
        return lineNumber;
    }

    private int getLineNumberNotUnique(String str,int index){
        int lineNumber = 0;
        String[] lines=str.split("\n");
        int tagNum=0;
        for (int i=0;i<lines.length;i++){
            if ((lines[i].length()>0&&lines[i].charAt(0)=='#')||(lines[i].length()>2&&lines[i].substring(0,3).matches("<h[1-6]"))){
                tagNum++;
                if (tagNum==index) {
                    lineNumber = i;
                    break;
                }
            }
        }
        return lineNumber;
    }

    private void addCssStyle(){
        mdhtml="<style type=\"text/css\">\n"+css+"\n</style>\n"+mdhtml;
    }

    private void setCssStyle(String cssStyle){
        css = cssStyle;
    }

    private void setCssDialog(){
        cssDialog = new JDialog();
        final JTextArea cssText = new JTextArea();
        cssText.setLineWrap(true);
        JButton confirm = new JButton("确认");
        JButton cancel = new JButton("取消");
        JPanel buttonGroup = new JPanel(new GridLayout(1,2));
        buttonGroup.add(confirm);
        buttonGroup.add(cancel);
        confirm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setCssStyle(cssText.getText().toString());
                changed();
                cssDialog.dispose();
                status.setText("外部样式已更改。");
            }
        });
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cssDialog.dispose();
            }
        });
        cssDialog.add(new JLabel("请在下方编辑正确的CSS代码。"),BorderLayout.NORTH);
        cssDialog.add(cssText);
        cssDialog.add(buttonGroup,BorderLayout.SOUTH);
        cssDialog.setSize(300,300);
        cssDialog.setLocation(200,200);
    }

    private void setMenuItems(){
        JMenu fileMenu = new JMenu("文件");
        JMenuItem exit = new JMenuItem("退出");
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        exit.setAccelerator(KeyStroke.getKeyStroke("ctrl Q"));
        JMenuItem open = new JMenuItem("打开");
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFile();
            }
        });
        open.setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        JMenuItem save = new JMenuItem("保存");
        save.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFile(textAreaInput.getText().toString(),"md",false);
            }
        });
        JMenuItem saveAs = new JMenuItem("另存为");
        saveAs.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFile(textAreaInput.getText().toString(),"md",true);
            }
        });
        JMenuItem export2Html = new JMenuItem("html");
        export2Html.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveFile(mdShow.getText().toString(),"html",false);
            }
        });
        JMenuItem export2Docx = new JMenuItem("docx");
        export2Docx.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveDocx(mdShow.getText().toString());
            }
        });
        JMenu export = new JMenu("输出");
        export.add(export2Html);
        export.add(export2Docx);
        fileMenu.add(open);
        fileMenu.add(save);
        fileMenu.add(saveAs);
        fileMenu.add(export);
        fileMenu.addSeparator();
        fileMenu.add(exit);
        JMenu edit = new JMenu("编辑");
        JMenuItem editCss = new JMenuItem("样式");
        editCss.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cssDialog.show();
            }
        });
        edit.add(editCss);
        JMenu help = new JMenu("帮助");
        JMenuItem readme = new JMenuItem("使用说明");
        readme.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null,readmeTip,"使用说明",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        help.add(readme);
        menuBar.add(fileMenu);
        menuBar.add(edit);
        menuBar.add(help);
    }

    private boolean saveFile(String content, String type,Boolean saveAs){
        if (file == null || saveAs || !type.equals("md")) {
            JFileChooser saveFc=new JFileChooser();//创建文件选择器
            saveFc.showSaveDialog(new JLabel("保存"+type));
            file = saveFc.getSelectedFile();
            if (file == null) {
                return false;
            } else if (file.exists()) {//已存在文件
                int op = JOptionPane.showConfirmDialog(saveFc, file + "文件已经存在,是否覆盖!", "文件存在", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);//显示一个对话框来实现是否覆盖源文件
                if (op != 0) {
                    return false;
                }
            }
        }
        //如果保存md文件时未打开过文件或者是另存为的操作,那么需要进行一次选择
        //否则file默认为上一次的file

        FileOutputStream fous = null;
        try {
            fous = new FileOutputStream(file);//写入到这个目录中
            fous.write(content.getBytes());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,"文件保存失败!","文件未保存",JOptionPane.INFORMATION_MESSAGE);
            ex.printStackTrace();
        } finally {
            try {
                fous.close();
                status.setText("文件已保存");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,"文件保存失败!","文件未保存",JOptionPane.INFORMATION_MESSAGE);
                ex.printStackTrace();
            }
        }
        return true;
    }

    private boolean saveDocx(String content){
        JFileChooser saveFc=new JFileChooser();//创建文件选择器
        saveFc.showSaveDialog(new JLabel("保存Docx"));

        File file = saveFc.getSelectedFile();
        if(file.exists()){//已存在文件
            int op = JOptionPane.showConfirmDialog(saveFc, file + "文件已经存在,是否覆盖!", "文件存在", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);//显示一个对话框来实现是否覆盖源文件
            if (op!=0){
                return false;
            }
        }

        final File swFile = file;
        final String swContent = content;
        status.setText("转换中...0%");

        Thread thread = new Thread(){
            @Override
            public void run(){
                try {
                    converter.setFilePath(swFile.getPath());
                    status.setText("转换中...30%");
                    converter.saveHtmlToDocx(swContent);
                    status.setText("已输出文件...100%");
                    sleep(2000);
                    status.setText("");
                }
                catch (Exception e){
                    JOptionPane.showMessageDialog(null,"文件保存失败!","文件未保存",JOptionPane.INFORMATION_MESSAGE);
                    e.printStackTrace();
                }
            }
        };

        thread.start();

        return true;
    }

    private void openFile() {
        JFileChooser openFc=new JFileChooser();//创建文件选择器
        openFc.showOpenDialog(new JLabel("打开"));
        File file = openFc.getSelectedFile();
        if(file==null||!file.exists()){//存在文件
            return;
        }
        try{
            FileInputStream in = new FileInputStream(file);
            byte[] inByte = new byte[in.available()];
            in.read(inByte);
            String fileContent = new String(inByte);
            textAreaInput.setText(fileContent);
        }
        catch (Exception e){
            JOptionPane.showMessageDialog(this,"文件打开失败!","文件未打开",JOptionPane.INFORMATION_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new myMarkdown();
            }
        });
    }
}