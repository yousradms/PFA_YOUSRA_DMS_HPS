package com.hps.Sandbox.projetSandbox.Controller;

import com.hps.Sandbox.projetSandbox.Model.ApiRequest;
import com.hps.Sandbox.projetSandbox.Service.ApiService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class ApiController {

    @Autowired
    private ApiService apiService;

    @GetMapping("/")
    public String showInputForm(Model model) {
        model.addAttribute("apiRequest", new ApiRequest());
        return "input";
    }

    @PostMapping("/process")
    public String processApi(@ModelAttribute ApiRequest apiRequest, @RequestParam("openapiFile") MultipartFile openapiFile, Model model) {
        try {
            // Save the uploaded file temporarily
            Path tempFile = Files.createTempFile("openapi", ".json");
            Files.copy(openapiFile.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String openapiPath = tempFile.toString();

            Map<String, Object> result = apiService.processApi(apiRequest, openapiPath);
            if (result.containsKey("error")) {
                model.addAttribute("error", result.get("error"));
            } else {
                String successMessage = (String) result.get("success");
                model.addAttribute("success", successMessage);
                model.addAttribute("stats", result.get("stats"));
                model.addAttribute("generatedFiles", result.get("generatedFiles"));
                String outputDir = successMessage.substring("Fichiers générés dans le dossier ".length());
                model.addAttribute("outputDir", outputDir);
            }
            // Clean up the temporary file after processing
            Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du traitement : " + e.getMessage());
        }
        return "result";
    }

    @GetMapping("/download/**")
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request, @RequestParam String outputDir) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        AntPathMatcher apm = new AntPathMatcher();
        String fileName = apm.extractPathWithinPattern(bestMatchPattern, path);

        File file = new File(outputDir, fileName);
        if (!file.exists() || !file.getAbsolutePath().startsWith(new File(outputDir).getAbsolutePath())) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName());
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(resource);
    }

    @GetMapping("/download/all")
    public ResponseEntity<Resource> downloadAllFiles(@RequestParam("outputsyaml") String outputDir) throws IOException {
        Path outputPath = Paths.get(outputDir);
        if (!Files.exists(outputPath)) {
            return ResponseEntity.notFound().build();
        }

        Path zipFile = Files.createTempFile("yaml-files", ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walk(outputPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yaml"))
                .forEach(path -> {
                    try {
                        String entryName = outputPath.relativize(path).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }

        Resource resource = new FileSystemResource(zipFile.toFile());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=api-files.zip");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(zipFile.toFile().length())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(resource);
    }
}