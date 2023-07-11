package com.sanluan.einvoice.web;

import com.sanluan.einvoice.service.Invoice;
import com.sanluan.einvoice.service.OfdInvoiceExtractor;
import com.sanluan.einvoice.service.PdfInvoiceExtractor;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.config.RequestConfig;
import org.dom4j.DocumentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/invoice")
public class InvoiceController {

    @Value("${backupPath}")
    private String backupPath;

    private static ThreadLocal<Map<String, DateFormat>> threadLocal = new ThreadLocal<>();
    private static final String FILE_NAME_FORMAT_STRING = "yyyy/MM-dd/HH-mm-ssSSSS";
    public static final RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(5000)
            .setConnectionRequestTimeout(5000).build();

    /**
     * @param pattern
     * @return date format
     */
    public static DateFormat getDateFormat(String pattern) {
        Map<String, DateFormat> map = threadLocal.get();
        DateFormat format = null;
        if (null == map) {
            map = new HashMap<>();
            format = new SimpleDateFormat(pattern);
            map.put(pattern, format);
            threadLocal.set(map);
        } else {
            format = map.computeIfAbsent(pattern, k -> new SimpleDateFormat(k));
        }
        return format;
    }

    @RequestMapping(value = "/extrat")
    public String extrat() throws IOException {

        //todo  路径需要修改
        String path = "D:\\余紫婕发票（6月备用金）";
        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            List<Path> fileNames = paths.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path currentFile : fileNames) {
                if (!currentFile.getFileName().toString().toLowerCase().endsWith(".ofd") && !currentFile.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                    System.out.println(currentFile.getFileName().toString());
                    continue;
                }
                String currentFileName = path + "\\" + currentFile.getFileName().toString();
                MultipartFile file = convertFileToMultipartFile(currentFileName);
                String fileName = getDateFormat(FILE_NAME_FORMAT_STRING).format(new Date());
                File dest = null;
                boolean ofd = false;
                if (null != file && !file.isEmpty()) {
                    if (file.getOriginalFilename().toLowerCase().endsWith(".ofd")) {
                        ofd = true;
                        dest = new File(backupPath, fileName + ".ofd");
                    } else {
                        dest = new File(backupPath, fileName + ".pdf");
                    }
                    dest.getParentFile().mkdirs();
                    try {
                        FileUtils.copyInputStreamToFile(file.getInputStream(), dest);
                    } catch (IOException e) {
                    }
                }

                Invoice result = null;
                try {
                    if (null != dest) {
                        if (ofd) {
                            result = OfdInvoiceExtractor.extract(dest);
                        } else {
                            result = PdfInvoiceExtractor.extract(dest);
                        }
                        if (null != result.getAmount()) {
                            dest.delete();
                        }
                    } else {
                        result = new Invoice();
                        result.setTitle("error");
                    }
                } catch (IOException | DocumentException e) {
                    e.printStackTrace();
                    result = new Invoice();
                    result.setTitle("error");
                }


                String originalName = file.getOriginalFilename();
                String ext = "." + FilenameUtils.getExtension(originalName);
                // 新生成的文件名称
                String newFileName = result.getNumber() + ext;
                if (result.getNumber() == null) {
                    newFileName = originalName;
                }
                // todo 路径需要修改 保存复制文件
                File targetFile = new File("D:\\invoice", newFileName);
                FileUtils.writeByteArrayToFile(targetFile, file.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "完成";
    }


    public MultipartFile convertFileToMultipartFile(String filePath) throws IOException {
        File file = new File(filePath);
        InputStream inputStream = new FileInputStream(file);

        // 使用DiskFileItem构造一个临时的文件项
        DiskFileItem fileItem = new DiskFileItem("file", "application/octet-stream", false, file.getName(), (int) file.length(), file.getParentFile());

        // 将文件内容写入临时文件项
        fileItem.getOutputStream();
        try (InputStream input = inputStream) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                fileItem.getOutputStream().write(buffer, 0, bytesRead);
            }
        }

        // 创建一个CommonsMultipartFile对象，将临时文件项包装为MultipartFile类型
        MultipartFile multipartFile = new CommonsMultipartFile(fileItem);

        return multipartFile;
    }
}
