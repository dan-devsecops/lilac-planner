output "cluster_name" {
  description = "-> GitHub Actions Variable DO_CLUSTER_NAME"
  value       = digitalocean_kubernetes_cluster.this.name
}

output "cluster_id" {
  value = digitalocean_kubernetes_cluster.this.id
}

output "region" {
  description = "-> GitHub Actions Variable DO_REGION"
  value       = var.region
}

output "registry_name" {
  description = "-> GitHub Actions Variable DO_REGISTRY_NAME"
  value       = local.registry_name
}

output "registry_endpoint" {
  description = "Image prefix for `docker push` / Helm's image.registry - registry.digitalocean.com/<name>, NOT just the host."
  value       = local.registry_endpoint
}

output "vpc_id" {
  value = digitalocean_vpc.this.id
}

output "project_id" {
  value = digitalocean_project.this.id
}

output "db_host" {
  description = "Private host - only reachable from inside the VPC (i.e. from the DOKS nodes)."
  value       = digitalocean_database_cluster.postgres.private_host
}

output "db_port" {
  value = digitalocean_database_cluster.postgres.port
}

output "db_user" {
  description = "-> GitHub Actions Secret DB_USER"
  value       = digitalocean_database_user.app.name
}

output "db_password" {
  description = "-> GitHub Actions Secret DB_PASSWORD. Sensitive - fetch with: terraform output -raw db_password"
  value       = digitalocean_database_user.app.password
  sensitive   = true
}

output "db_admin_user" {
  description = "Sensitive - fetch with: terraform output -raw db_admin_user"
  value       = digitalocean_database_cluster.postgres.user
  sensitive   = true
}

output "db_admin_password" {
  description = "Sensitive - fetch with: terraform output -raw db_admin_password"
  value       = digitalocean_database_cluster.postgres.password
  sensitive   = true
}

output "db_url" {
  description = "-> GitHub Actions Secret DB_URL. Sensitive - fetch with: terraform output -raw db_url"
  value       = "jdbc:postgresql://${digitalocean_database_cluster.postgres.private_host}:${digitalocean_database_cluster.postgres.port}/${var.db_name}?sslmode=require"
  sensitive   = true
}
