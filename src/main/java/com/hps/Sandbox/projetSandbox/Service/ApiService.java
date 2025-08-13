package com.hps.Sandbox.projetSandbox.Service;

import com.hps.Sandbox.projetSandbox.Model.ApiRequest;
import com.hps.Sandbox.projetSandbox.Util.SchemaProcessor;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ApiService {

    private Map<String, Object> openapi;

    public Map<String, Object> processApi(ApiRequest request, String openapiPath) throws IOException 
    {
        String selectedTag = request.getTag().trim();
        String version = request.getVersion() != null ? request.getVersion().trim() : "";
        boolean singleFile = request.getOutputChoice().equals("1");

        Map<String, Object> result = new HashMap<>();
        List<String> generatedFiles = new ArrayList<>();

        // Load OpenAPI file from uploaded path
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(10_000_000);
        Yaml yaml = new Yaml(loaderOptions);
        try (InputStream inputStream = new FileInputStream(openapiPath)) {
            openapi = yaml.load(inputStream);
        }

        Map<String, Object> paths = (Map<String, Object>) openapi.get("paths");
        Set<String> availableVersions = new HashSet<>();
        if (version.isEmpty()) {
            if (paths != null) {
                for (String path : paths.keySet()) {
                    if (path.contains("/v") || path.contains("/V")) {
                        String[] parts = path.split("/");
                        for (String part : parts) {
                            if (part.matches("[vV]\\d+")) {
                                availableVersions.add(part.toUpperCase());
                            }
                        }
                    }
                }
            }
            if (availableVersions.isEmpty()) {
                availableVersions.add("NO_VERSION"); // Fallback for no versions
            }
        } else {
            if (!version.matches("[vV]\\d+")) {
                result.put("error", "Version invalide. La version doit être au format 'v' ou 'V' suivi de chiffres (ex: v1, V1).");
                return result;
            }
            availableVersions.add(version.toUpperCase());
        }

        if (!request.getOutputChoice().equals("1") && !request.getOutputChoice().equals("2")) {
            result.put("error", "Choix invalide. Veuillez entrer 1 pour un seul fichier ou 2 pour des fichiers séparés.");
            return result;
        }

        StringBuilder stats = new StringBuilder();
        stats.append("🔍 Recherche de l'API: ").append(selectedTag).append("\n");
        stats.append("📊 Statistiques de traitement:\n");

        Path mainOutputDir = Paths.get("C:\\Users\\hp\\OneDrive\\Bureau\\testHPS", selectedTag);
        if (!Files.exists(mainOutputDir)) {
            Files.createDirectories(mainOutputDir);
        }

        for (String currentVersion : availableVersions) {
            Map<String, Object> extractedPaths = new LinkedHashMap<>();
            Set<String> usedSchemasRequest = new HashSet<>();
            Set<String> usedSchemasResponse = new HashSet<>();
            Set<String> allReferencedSchemas = new HashSet<>();
            Set<String> arraySchemas = new HashSet<>();

            SchemaProcessor.processPaths(openapi, paths, selectedTag, currentVersion, extractedPaths, usedSchemasRequest, 
                usedSchemasResponse, allReferencedSchemas, arraySchemas, singleFile);

            stats.append("   Version ").append(currentVersion).append(":\n");
            stats.append("      - Chemins trouvés: ").append(extractedPaths.size()).append("\n");
            stats.append("      - Schémas Request collectés: ").append(usedSchemasRequest.size()).append("\n");
            stats.append("      - Schémas Response collectés: ").append(usedSchemasResponse.size()).append("\n");
            stats.append("      - Toutes références collectées: ").append(allReferencedSchemas.size()).append("\n");
            stats.append("      - Schémas de type array: ").append(arraySchemas.size()).append("\n");

            if (extractedPaths.isEmpty()) {
                continue;
            }

            Path outputDir = singleFile ? mainOutputDir : mainOutputDir.resolve(currentVersion);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            String combinedName = selectedTag + "_" + currentVersion;
            SchemaProcessor.generateYamlFiles(outputDir, combinedName, extractedPaths, usedSchemasRequest, 
                usedSchemasResponse, allReferencedSchemas, arraySchemas, singleFile, openapi);

            if (singleFile) {
                generatedFiles.add(mainOutputDir.relativize(outputDir.resolve(combinedName + ".yaml")).toString().replace("\\", "/"));
            } else {
                generatedFiles.add(mainOutputDir.relativize(outputDir.resolve(combinedName + ".yaml")).toString().replace("\\", "/"));
                generatedFiles.add(mainOutputDir.relativize(outputDir.resolve("request.yaml")).toString().replace("\\", "/"));
                generatedFiles.add(mainOutputDir.relativize(outputDir.resolve("response.yaml")).toString().replace("\\", "/"));
                generatedFiles.add(mainOutputDir.relativize(outputDir.resolve("aggregate.yaml")).toString().replace("\\", "/"));
            }

            result.put("success", "Fichiers générés dans le dossier " + mainOutputDir.toAbsolutePath());
        }

        if (result.containsKey("success")) {
            result.put("stats", stats.toString());
            result.put("generatedFiles", generatedFiles);
        } else {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Aucun chemin trouvé pour l'API '").append(selectedTag).append("'\n");
            if (availableVersions.contains("NO_VERSION")) {
                errorMsg.append("⚠️ Aucune version détectée dans le fichier OpenAPI. Vérifiez que les chemins contiennent des segments comme '/v1' ou '/V1'.\n");
            } else {
                errorMsg.append("🔍 Vérifiez que le tag existe dans le fichier OpenAPI pour les versions détectées: ").append(availableVersions).append("\n");
            }
            errorMsg.append("💡 Tags disponibles dans le fichier:\n");
            Set<String> availableTags = new HashSet<>();
            if (paths != null) {
                for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                    Map<String, Object> methods = (Map<String, Object>) pathEntry.getValue();
                    for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                        Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                        List<String> tags = (List<String>) operation.get("tags");
                        if (tags != null) {
                            availableTags.addAll(tags);
                        }
                    }
                }
            }
            errorMsg.append("Tags disponibles:\n");
            availableTags.stream().sorted().limit(20).forEach(tag -> errorMsg.append("   - ").append(tag).append("\n"));
            if (availableTags.size() > 20) {
                errorMsg.append("   ... et ").append(availableTags.size() - 20).append(" autres tags\n");
            }
            errorMsg.append("Versions disponibles:\n");
            availableVersions.stream().sorted().limit(20).forEach(ver -> errorMsg.append("   - ").append(ver).append("\n"));
            if (availableVersions.size() > 20) {
                errorMsg.append("   ... et ").append(availableVersions.size() - 20).append(" autres versions\n");
            }
            result.put("error", errorMsg.toString());
        }

        return result;
    }
}