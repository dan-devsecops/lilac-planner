# Docker repo for the backend & frontend images.
resource "google_artifact_registry_repository" "this" {
  location      = var.region
  repository_id = "${var.prefix}-images"
  format        = "DOCKER"
}
