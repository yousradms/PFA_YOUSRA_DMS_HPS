Aperçu
projetSandbox est une application Spring Boot conçue pour traiter des spécifications OpenAPI, générer des fichiers YAML filtrés en fonction des entrées de l'utilisateur et permettre le téléchargement des fichiers générés. L'application permet de traiter des fichiers OpenAPI pour extraire des chemins et des schémas spécifiques en fonction d'un tag et d'une version sélectionnés, et offre des options pour générer soit un seul fichier YAML, soit plusieurs fichiers (principal, requête, réponse et agrégé).
Fonctionnalités

Formulaire d'entrée : Les utilisateurs peuvent uploader un fichier OpenAPI JSON, spécifier un tag, une version et un choix de sortie (fichier unique ou fichiers multiples).
Traitement de l'API : Traite le fichier OpenAPI uploadé pour extraire les chemins et schémas correspondant au tag et à la version spécifiés.
Génération de fichiers : Génère des fichiers YAML contenant les chemins et schémas filtrés. Prend en charge une sortie en fichier unique ou en fichiers multiples.
Téléchargement de fichiers : Permet de télécharger des fichiers YAML individuels ou tous les fichiers générés sous forme d'archive ZIP.
Gestion des erreurs : Fournit des messages d'erreur détaillés et des statistiques pour le dépannage.

Structure du projet

Contrôleur : ApiController.java

Gère les requêtes HTTP pour le formulaire d'entrée, le traitement de l'API et les téléchargements de fichiers.
Points d'entrée :

GET / : Affiche le formulaire d'entrée.
POST /process : Traite le fichier OpenAPI uploadé et génère des fichiers YAML.
GET /download/** : Télécharge les fichiers YAML individuels générés.
GET /download/all : Télécharge tous les fichiers YAML générés sous forme d'archive ZIP.




Modèle : ApiRequest.java

Représente les données d'entrée de l'utilisateur (tag, version, choix de sortie).


Service : ApiService.java

Contient la logique principale pour traiter le fichier OpenAPI, extraire les chemins et générer les fichiers YAML.


Utilitaire : SchemaProcessor.java

Gère l'extraction, la validation des schémas et la génération des fichiers YAML.
