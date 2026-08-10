output "region" {
  value = var.region
}

output "artifact_registry_host" {
  description = "Registry host for image.registry / docker push."
  value       = "${var.region}-docker.pkg.dev"
}

output "image_repo_prefix" {
  description = "Full image.registry prefix (host/project/repo)."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.this.repository_id}"
}

output "gke_cluster_name" {
  value = google_container_cluster.this.name
}

output "cloudsql_private_ip" {
  value = google_sql_database_instance.this.private_ip_address
}

output "jdbc_url" {
  value = "jdbc:mariadb://${google_sql_database_instance.this.private_ip_address}:3306/${var.db_name}"
}

# -------- GitHub Actions config (for google-github-actions/auth) --------
output "workload_identity_provider" {
  description = "-> GitHub secret GCP_WIF_PROVIDER."
  value       = var.enable_github_oidc ? google_iam_workload_identity_pool_provider.github[0].name : null
}

output "github_service_account_email" {
  description = "-> GitHub secret GCP_DEPLOY_SA."
  value       = var.enable_github_oidc ? google_service_account.github[0].email : null
}
