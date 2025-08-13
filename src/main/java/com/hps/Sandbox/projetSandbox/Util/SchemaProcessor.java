package com.hps.Sandbox.projetSandbox.Util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("unchecked")
public class SchemaProcessor {

    public static void processPaths(Map<String, Object> openapi, Map<String, Object> paths, String selectedTag, String upperVersion,
                                   Map<String, Object> extractedPaths, Set<String> usedSchemasRequest,
                                   Set<String> usedSchemasResponse, Set<String> allReferencedSchemas,
                                   Set<String> arraySchemas, boolean singleFile) {
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                if (!upperVersion.equals("NO_VERSION") && !path.toUpperCase().contains(upperVersion)) continue;

                Map<String, Object> methods = (Map<String, Object>) pathEntry.getValue();
                Map<String, Object> filteredMethods = new LinkedHashMap<>();

                for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                    String method = methodEntry.getKey();
                    Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                    List<String> tags = (List<String>) operation.get("tags");

                    if (tags != null) {
                        boolean match = tags.stream().anyMatch(t -> {
                            String cleanTag = t.replaceAll("\\s+", "").toLowerCase();
                            String cleanSelected = selectedTag.replaceAll("\\s+", "").toLowerCase();
                            boolean matches = cleanTag.equals(cleanSelected); // Exact match
                            if (matches) {
                                System.out.println("✅ Match trouvé: '" + t + "' correspond à '" + selectedTag + "' pour le chemin " + path);
                            }
                            return matches;
                        });
                        if (match) {
                            operation.put("tags", Collections.singletonList(selectedTag.replaceAll("([a-z])([A-Z])", "$1 $2")));

                            Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
                            if (responses != null) {
                                for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                                    String responseCode = responseEntry.getKey();
                                    Object responseValue = responseEntry.getValue();
                                    if (responseValue instanceof Map) {
                                        Map<String, Object> responseMap = (Map<String, Object>) responseValue;
                                        if (!responseMap.containsKey("description")) {
                                            responseMap.put("description", "Default response for " + responseCode);
                                        }
                                    }
                                }
                            }

                            filteredMethods.put(method, operation);

                            Map<String, Object> requestBody = (Map<String, Object>) operation.get("requestBody");
                            if (requestBody != null) {
                                Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
                                if (content != null) {
                                    for (Object mediaType : content.values()) {
                                        Map<String, Object> mediaMap = (Map<String, Object>) mediaType;
                                        Map<String, Object> schema = (Map<String, Object>) mediaMap.get("schema");
                                        if (schema != null) {
                                            processSchema(openapi, schema, usedSchemasRequest, allReferencedSchemas, arraySchemas, singleFile ? null : "./request.yaml");
                                        }
                                    }
                                }
                            }

                            if (responses != null) {
                                for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                                    String responseCode = responseEntry.getKey();
                                    Object responseValue = responseEntry.getValue();
                                    if (responseValue instanceof Map) {
                                        Map<String, Object> responseMap = (Map<String, Object>) responseValue;
                                        Map<String, Object> content = (Map<String, Object>) responseMap.get("content");
                                        if (content != null) {
                                            for (Object mediaTypeValue : content.values()) {
                                                Map<String, Object> mediaMap = (Map<String, Object>) mediaTypeValue;
                                                Map<String, Object> schema = (Map<String, Object>) mediaMap.get("schema");
                                                if (schema != null) {
                                                    processSchema(openapi, schema, usedSchemasResponse, allReferencedSchemas, arraySchemas, singleFile ? null : "./response.yaml");
                                                } else {
                                                    System.out.println("⚠️ Pas de schéma dans response " + responseCode + " pour " + path);
                                                }
                                            }
                                        } else {
                                            System.out.println("⚠️ Pas de content dans response " + responseCode + " pour " + path);
                                        }
                                    }
                                }
                            } else {
                                System.out.println("⚠️ Pas de responses dans l'opération " + method + " pour " + path);
                            }
                        }
                    }
                }

                if (!filteredMethods.isEmpty()) {
                    extractedPaths.put(path, filteredMethods);
                }
            }
        }
    }

    public static void generateYamlFiles(Path outputDir, String combinedName, Map<String, Object> extractedPaths,
                                        Set<String> usedSchemasRequest, Set<String> usedSchemasResponse,
                                        Set<String> allReferencedSchemas, Set<String> arraySchemas,
                                        boolean singleFile, Map<String, Object> openapi) throws IOException {
        Map<String, Object> extractedApi = new LinkedHashMap<>();
        extractedApi.put("openapi", "3.0.3");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "Payment Instrument API documentation");
        info.put("description", """
                Payment Instrument and Cardholder operation management include APIs for Payment Instrument maintenance, Payment instrument operations, and Status operation.
                """);
        info.put("version", "PowerCARD-Issuer 3.5.4-apiR5.3");
        extractedApi.put("info", info);

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "${sandbox.backend.connectApiUrl}/rest");
        server.put("description", "Development server");
        extractedApi.put("servers", Collections.singletonList(server));

        Map<String, Object> security = new LinkedHashMap<>();
        security.put("bearerAuth", new ArrayList<>());
        extractedApi.put("security", Collections.singletonList(security));

        extractedApi.put("paths", extractedPaths);

        Map<String, Object> extractedComponents = new LinkedHashMap<>();
        Map<String, Object> securitySchemes = new LinkedHashMap<>();
        Map<String, Object> bearerAuth = new LinkedHashMap<>();
        bearerAuth.put("type", "http");
        bearerAuth.put("scheme", "bearer");
        bearerAuth.put("bearerFormat", "JWT");
        bearerAuth.put("description", """
                <div>
                  <h5>Api key authorization</h5>
                  <p>JWT authorization header using Bearer scheme. Example: "Authorization: Bearer {token}"</p>
                  <table>
                    <tr><td>Name:</td><td>Authorization</td></tr>
                    <tr><td>In:</td><td>Header</td></tr>
                  </table>
                </div>
                """);
        securitySchemes.put("bearerAuth", bearerAuth);
        extractedComponents.put("securitySchemes", securitySchemes);

        Map<String, Object> components = (Map<String, Object>) openapi.get("components");
        Map<String, Object> allSchemas = components != null ? (Map<String, Object>) components.get("schemas") : new HashMap<>();

        if (singleFile) {
    Map<String, Object> combinedSchemas = new LinkedHashMap<>();
    boolean missingSchema = false;
    for (String schemaName : usedSchemasRequest) {
        if (allSchemas.containsKey(schemaName)) {
            Map<String, Object> schemaDef = deepCopy((Map<String, Object>) allSchemas.get(schemaName));
            validateAndUpdateSchemaSingleFile(schemaDef, arraySchemas);
            removeUnwantedProperties(schemaDef);
            combinedSchemas.put(schemaName, schemaDef);
            System.out.println("✅ Schéma Request ajouté: " + schemaName);
        } else {
            System.out.println("❌ Schéma Request non trouvé: " + schemaName);
            missingSchema = true;
        }
    }
    for (String schemaName : usedSchemasResponse) {
        if (allSchemas.containsKey(schemaName)) {
            Map<String, Object> schemaDef = deepCopy((Map<String, Object>) allSchemas.get(schemaName));
            try {
                // Ne pas ajouter responseInfo dynamiquement, laisser les schémas tels quels
                validateAndUpdateSchemaSingleFile(schemaDef, arraySchemas);
                removeUnwantedProperties(schemaDef);
                combinedSchemas.put(schemaName, schemaDef);
                System.out.println("✅ Schéma Response ajouté: " + schemaName);
            } catch (Exception e) {
                System.out.println("❌ Erreur lors du traitement du schéma Response " + schemaName + ": " + e.getMessage());
                missingSchema = true;
            }
        } else {
            System.out.println("❌ Schéma Response non trouvé dans allSchemas: " + schemaName);
            missingSchema = true;
        }
    }
    Set<String> processed = new HashSet<>();
    for (String schemaName : allReferencedSchemas) {
        if (!processed.contains(schemaName) && allSchemas.containsKey(schemaName)) {
            processed.add(schemaName);
            Map<String, Object> schemaCopy = deepCopy((Map<String, Object>) allSchemas.get(schemaName));
            updateReferencesToInternal(schemaCopy);
            removeUnwantedProperties(schemaCopy);
            combinedSchemas.put(schemaName, schemaCopy);
            System.out.println("✅ Schéma référencé ajouté: " + schemaName);
        } else if (!allSchemas.containsKey(schemaName)) {
            System.out.println("⚠️ Schéma référencé ignoré car absent: " + schemaName);
        }
    }
    if (missingSchema) {
        System.out.println("⚠️ Certains schémas sont manquants ou ont échoué, vérifiez le fichier OpenAPI.");
        throw new IOException("Un ou plusieurs schémas référencés sont manquants ou ont échoué dans le traitement.");
    }
    extractedComponents.put("schemas", combinedSchemas);
    extractedApi.put("components", extractedComponents);
    String outputFile = outputDir.resolve(combinedName + ".yaml").toString();
    System.out.println("Écriture du fichier YAML: " + outputFile);
    try {
        writeYamlFile(outputFile, extractedApi);
        System.out.println("✅ Fichier généré : " + outputFile);
    } catch (IOException e) {
        System.out.println("❌ Erreur lors de l'écriture du fichier " + outputFile + ": " + e.getMessage());
        throw e;
    }
}else {
            Map<String, Object> schemasRefs = new LinkedHashMap<>();
            for (String schemaName : usedSchemasRequest) {
                schemasRefs.put(schemaName, Map.of("$ref", "./request.yaml#/" + schemaName));
            }
            for (String schemaName : usedSchemasResponse) {
                schemasRefs.put(schemaName, Map.of("$ref", "./response.yaml#/" + schemaName));
            }
            extractedComponents.put("schemas", schemasRefs);
            extractedApi.put("components", extractedComponents);

            Map<String, Object> requestSchemas = new LinkedHashMap<>();
            for (String schemaName : usedSchemasRequest) {
                if (allSchemas.containsKey(schemaName)) {
                    Map<String, Object> schemaDef = deepCopy((Map<String, Object>) allSchemas.get(schemaName));
                    validateAndUpdateSchema(schemaDef, arraySchemas, "./aggregate.yaml");
                    removeUnwantedProperties(schemaDef);
                    requestSchemas.put(schemaName, schemaDef);
                } else {
                    System.out.println("❌ Schéma Request non trouvé: " + schemaName);
                }
            }

            Map<String, Object> responseSchemas = new LinkedHashMap<>();
            System.out.println("🔧 Création des schémas Response. Schémas trouvés: " + usedSchemasResponse);
            for (String schemaName : usedSchemasResponse) {
                System.out.println("🔍 Traitement du schéma Response: " + schemaName);
                if (allSchemas.containsKey(schemaName)) {
                    Map<String, Object> schemaDef = deepCopy((Map<String, Object>) allSchemas.get(schemaName));
                    if (!"array".equals(schemaDef.get("type"))) {
                        Map<String, Object> properties = (Map<String, Object>) schemaDef.getOrDefault("properties", new LinkedHashMap<>());
                        List<String> required = new ArrayList<>((List<String>) schemaDef.getOrDefault("required", new ArrayList<>()));
                        if (!properties.containsKey("responseInfo")) {
                            properties.put("responseInfo", Map.of("$ref", "./aggregate.yaml#/ResponseInfo"));
                            if (!required.contains("responseInfo")) {
                                required.add(0, "responseInfo");
                            }
                            allReferencedSchemas.add("ResponseInfo");
                        }
                        schemaDef.put("properties", properties);
                        schemaDef.put("required", required);
                    }
                    validateAndUpdateSchema(schemaDef, arraySchemas, "./aggregate.yaml");
                    removeUnwantedProperties(schemaDef);
                    responseSchemas.put(schemaName, schemaDef);
                    System.out.println("✅ Schéma Response ajouté: " + schemaName);
                } else {
                    System.out.println("❌ Schéma Response non trouvé dans allSchemas: " + schemaName);
                }
            }

            Map<String, Object> aggregateSchemas = new LinkedHashMap<>();
            Set<String> processed = new HashSet<>();
            for (String schemaName : allReferencedSchemas) {
                if (!processed.contains(schemaName) && allSchemas.containsKey(schemaName)) {
                    processed.add(schemaName);
                    Map<String, Object> schemaCopy = deepCopy((Map<String, Object>) allSchemas.get(schemaName));
                    updateReferencesToAggregateForAggregateFile(schemaCopy);
                    removeUnwantedProperties(schemaCopy);
                    aggregateSchemas.put(schemaName, schemaCopy);
                }
            }

            writeYamlFile(outputDir.resolve(combinedName + ".yaml").toString(), extractedApi);
            writeYamlFile(outputDir.resolve("request.yaml").toString(), requestSchemas);
            writeYamlFile(outputDir.resolve("response.yaml").toString(), responseSchemas);
            writeYamlFile(outputDir.resolve("aggregate.yaml").toString(), aggregateSchemas);

            System.out.println("✅ Fichiers générés dans le dossier " + outputDir.toAbsolutePath());
            System.out.println("   - " + outputDir.resolve(combinedName + ".yaml"));
            System.out.println("   - " + outputDir.resolve("request.yaml"));
            System.out.println("   - " + outputDir.resolve("response.yaml"));
            System.out.println("   - " + outputDir.resolve("aggregate.yaml"));
            System.out.println("📊 Statistiques:");
            System.out.println("   - Schémas Request: " + requestSchemas.size());
            System.out.println("   - Schémas Response: " + responseSchemas.size());
            System.out.println("   - Schémas Aggregate: " + aggregateSchemas.size());
        }
    }

    public static void processSchema(Map<String, Object> openapi, Map<String, Object> schema, Set<String> usedSchemas, Set<String> allReferencedSchemas,
                                    Set<String> arraySchemas, String targetFile) {
        String ref = (String) schema.get("$ref");
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
            usedSchemas.add(schemaName);
            if (targetFile != null) {
                schema.put("$ref", targetFile + "#/" + schemaName);
            } else {
                schema.put("$ref", "#/components/schemas/" + schemaName);
            }
            collectAllReferencedSchemas(openapi, schemaName, allReferencedSchemas, arraySchemas);
        } else if ("array".equals(schema.get("type"))) {
            Map<String, Object> items = (Map<String, Object>) schema.get("items");
            if (items != null && items.containsKey("$ref")) {
                String itemRef = (String) items.get("$ref");
                if (itemRef != null && itemRef.startsWith("#/components/schemas/")) {
                    String itemSchemaName = itemRef.substring(itemRef.lastIndexOf("/") + 1);
                    usedSchemas.add(itemSchemaName);
                    arraySchemas.add(itemSchemaName);
                    if (targetFile != null) {
                        items.put("$ref", targetFile + "#/" + itemSchemaName);
                    } else {
                        items.put("$ref", "#/components/schemas/" + itemSchemaName);
                    }
                    collectAllReferencedSchemas(openapi, itemSchemaName, allReferencedSchemas, arraySchemas);
                }
            }
        }
    }

    public static void validateAndUpdateSchema(Map<String, Object> schemaDef, Set<String> arraySchemas, String targetFile) {
        if ("array".equals(schemaDef.get("type"))) {
            Map<String, Object> items = (Map<String, Object>) schemaDef.get("items");
            if (items != null && items.containsKey("$ref")) {
                String ref = (String) items.get("$ref");
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    String refName = ref.substring(ref.lastIndexOf("/") + 1);
                    items.put("$ref", targetFile + "#/" + refName);
                }
            }
        }
        updateReferencesToAggregate(schemaDef, targetFile);
    }

    public static void validateAndUpdateSchemaSingleFile(Map<String, Object> schemaDef, Set<String> arraySchemas) {
        if ("array".equals(schemaDef.get("type"))) {
            Map<String, Object> items = (Map<String, Object>) schemaDef.get("items");
            if (items != null && items.containsKey("$ref")) {
                String ref = (String) items.get("$ref");
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    String refName = ref.substring(ref.lastIndexOf("/") + 1);
                    items.put("$ref", "#/components/schemas/" + refName);
                }
            }
        }
        updateReferencesToInternal(schemaDef);
    }

    public static void removeUnwantedProperties(Map<String, Object> schemaDef) {
        Map<String, Object> properties = (Map<String, Object>) schemaDef.getOrDefault("properties", new HashMap<>());
        List<String> required = new ArrayList<>((List<String>) schemaDef.getOrDefault("required", new ArrayList<>()));
        properties.remove("keyValues");
        properties.remove("cardNumber");
        required.remove("keyValues");
        required.remove("cardNumber");
        if (required.isEmpty()) {
            schemaDef.remove("required");
        } else {
            schemaDef.put("required", required);
        }
        schemaDef.put("properties", properties);
    }

    public static void collectAllReferencedSchemas(Map<String, Object> openapi, String schemaName, Set<String> referencedSchemas, Set<String> arraySchemas) {
        if (referencedSchemas.contains(schemaName)) return;
        referencedSchemas.add(schemaName);

        Map<String, Object> components = (Map<String, Object>) openapi.get("components");
        Map<String, Object> allSchemas = components != null ? (Map<String, Object>) components.get("schemas") : new HashMap<>();
        Map<String, Object> schema = (Map<String, Object>) allSchemas.getOrDefault(schemaName, new HashMap<>());

        if ("array".equals(schema.get("type"))) {
            arraySchemas.add(schemaName);
        }

        Set<String> newRefs = new HashSet<>();
        collectSchemaReferences(schema, newRefs);
        for (String newRef : newRefs) {
            if (!referencedSchemas.contains(newRef)) {
                System.out.println("Collecte du schéma référencé : " + newRef);
                collectAllReferencedSchemas(openapi, newRef, referencedSchemas, arraySchemas);
            }
        }
    }

    public static void collectSchemaReferences(Object node, Set<String> references) {
        if (node instanceof Map) {
            Map<String, Object> mapNode = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : mapNode.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("$ref".equals(key) && value instanceof String) {
                    String ref = (String) value;
                    if (ref.startsWith("#/components/schemas/")) {
                        String refName = ref.substring(ref.lastIndexOf("/") + 1);
                        references.add(refName);
                    }
                } else {
                    collectSchemaReferences(value, references);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                collectSchemaReferences(item, references);
            }
        }
    }

    public static void updateReferencesToAggregate(Object node, String targetFile) {
        if (node instanceof Map) {
            Map<String, Object> mapNode = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : mapNode.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("$ref".equals(key) && value instanceof String) {
                    String ref = (String) value;
                    if (ref.startsWith("#/components/schemas/")) {
                        String refName = ref.substring(ref.lastIndexOf("/") + 1);
                        mapNode.put("$ref", targetFile + "#/" + refName);
                    }
                } else {
                    updateReferencesToAggregate(value, targetFile);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                updateReferencesToAggregate(item, targetFile);
            }
        }
    }

    public static void updateReferencesToAggregateForAggregateFile(Object node) {
        if (node instanceof Map) {
            Map<String, Object> mapNode = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : mapNode.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("$ref".equals(key) && value instanceof String) {
                    String ref = (String) value;
                    if (ref.startsWith("#/components/schemas/")) {
                        String refName = ref.substring(ref.lastIndexOf("/") + 1);
                        mapNode.put("$ref", "#/" + refName);
                    }
                } else {
                    updateReferencesToAggregateForAggregateFile(value);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                updateReferencesToAggregateForAggregateFile(item);
            }
        }
    }

    public static void updateReferencesToInternal(Object node) {
        if (node instanceof Map) {
            Map<String, Object> mapNode = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : mapNode.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("$ref".equals(key) && value instanceof String) {
                    String ref = (String) value;
                    if (ref.startsWith("#/components/schemas/")) {
                        String refName = ref.substring(ref.lastIndexOf("/") + 1);
                        mapNode.put("$ref", "#/components/schemas/" + refName);
                    }
                } else {
                    updateReferencesToInternal(value);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                updateReferencesToInternal(item);
            }
        }
    }

    public static Map<String, Object> deepCopy(Map<String, Object> original) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(key, deepCopy((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(key, deepCopyList((List<?>) value));
            } else {
                copy.put(key, value);
            }
        }
        return copy;
    }

    public static List<Object> deepCopyList(List<?> original) {
        List<Object> copy = new ArrayList<>();
        for (Object item : original) {
            if (item instanceof Map) {
                copy.add(deepCopy((Map<String, Object>) item));
            } else if (item instanceof List) {
                copy.add(deepCopyList((List<?>) item));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }

    public static void writeYamlFile(String filename, Object data) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(filename)) {
            yaml.dump(data, writer);
        }
    }
}