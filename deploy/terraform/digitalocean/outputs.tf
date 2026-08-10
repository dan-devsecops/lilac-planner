output "region" {
  value = var.region
}

output "registry_server" {
  description = "DOCR host for image.registry and docker push."
  value       = "registry.digitalocean.com/${digitalocean_container_registry.this.name}"
}

output "registry_name" {
  description = "DOCR registry name (used with doctl registry login)."
  value       = digitalocean_container_registry.this.name
}

output "doks_cluster_name" {
  description = "DOKS cluster name (used with doctl kubernetes cluster kubeconfig save)."
  value       = digitalocean_kubernetes_cluster.this.name
}

output "db_private_host" {
  description = "Private DB hostname - reachable from within the VPC (DOKS cluster)."
  value       = digitalocean_database_cluster.this.private_host
}

output "db_port" {
  description = "Database port (DO Managed MySQL uses 25060, not 3306)."
  value       = digitalocean_database_cluster.this.port
}

output "db_user" {
  value = digitalocean_database_user.app.name
}

output "db_password" {
  description = "Application database password - store as a GitHub/CI secret, never in git."
  value       = digitalocean_database_user.app.password
  sensitive   = true
}

output "jdbc_url" {
  description = "JDBC URL for the backend (private network, SSL required)."
  value       = "jdbc:mariadb://${digitalocean_database_cluster.this.private_host}:${digitalocean_database_cluster.this.port}/${var.db_name}?sslMode=REQUIRED"
}