package com.letsolve.oyster.controller;

import com.letsolve.oyster.entity.MiniPainting;
import com.letsolve.oyster.entity.Painting;
import com.letsolve.oyster.payload.UploadFileResponse;
import com.letsolve.oyster.redis.RedisHelper;
import com.letsolve.oyster.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class FileController {


    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    RedisHelper redisHelper;


    @GetMapping("like")
    public boolean like(String key, String ipAddr) {
        String value = redisHelper.get(key);
        MiniPainting m = new MiniPainting(value);

        m.setLike(m.getLike() + 1);

        redisHelper.save(key, m.toString());

        return true;
    }

    @GetMapping("dislike")
    public boolean dislike(String key, String ipAddr) {
        String value = redisHelper.get(key);
        MiniPainting m = new MiniPainting(value);
        m.setDislike(m.getDislike() + 1);

        redisHelper.save(key, m.toString());
        return false;
    }


    @GetMapping("gallery")
    public List<Painting> gallery() {

        Map<String, String> temp = redisHelper.getByPattern("image_*");

        List<Painting> list = new ArrayList();


        for (String s : temp.keySet()) {

            list.add(new Painting(s, temp.get(s)));
            System.out.println(s);
        }

        list.sort(Comparator.comparingInt(Painting::getLike));
        return list;
    }


    @PostMapping("submit")
    public UploadFileResponse submit(@RequestParam("file") MultipartFile file,
                                     @RequestParam String nickname,
                                     String name) {

        UploadFileResponse u = uploadFile(file);
        MiniPainting m = new MiniPainting();
        m.setUrl(u.getFileDownloadUri());

        redisHelper.save("image_" + nickname + "_" + name + "_" + UUID.randomUUID().toString().substring(4), m.toString());
        return u;
    }


    @PostMapping("/uploadFile")
    public UploadFileResponse uploadFile(@RequestParam("file") MultipartFile file) {
        String fileName = fileStorageService.storeFile(file);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(fileName)
                .toUriString();

        return new UploadFileResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }

    @PostMapping("/uploadMultipleFiles")
    public List<UploadFileResponse> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        return Arrays.asList(files)
                .stream()
                .map(file -> uploadFile(file))
                .collect(Collectors.toList());
    }

    @GetMapping("/downloadFile/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
        Resource resource = fileStorageService.loadFileAsResource(fileName);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

}