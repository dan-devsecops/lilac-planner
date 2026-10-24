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
  description = "For local/manual use (see README's smoke-deploy section). CI doesn't need this as a stored secret - cd-doks.yml looks it up live via 'doctl databases connection' using only DO_TOKEN."
  value       = digitalocean_database_user.app.name
}

output "db_password" {
  description = "For local/manual use. CI doesn't need this as a stored secret - see db_user's note. Sensitive - fetch with: terraform output -raw db_password"
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
  description = "For local/manual use (admin grants, debugging - see README). Sensitive - fetch with: terraform output -raw db_url"
  value       = "jdbc:postgresql://${digitalocean_database_cluster.postgres.private_host}:${digitalocean_database_cluster.postgres.port}/${var.db_name}?sslmode=require"
  sensitive   = true
}

output "db_pool_url" {
  description = "Writes go through the pool too (it's just a proxy in front of the same primary). prepareThreshold=0 disables pgjdbc server-side prepared statements, required under PgBouncer transaction-mode pooling - without it, statements get bound to a physical connection PgBouncer may swap out mid-session, surfacing as random 'prepared statement already exists' errors. For local/manual use only - CI builds this itself from 'doctl databases pool list'. null when enable_connection_pool = false. Sensitive - fetch with: terraform output -raw db_pool_url"
  value       = var.enable_connection_pool ? "${replace(digitalocean_database_connection_pool.app[0].private_uri, "postgresql://", "jdbc:postgresql://")}&prepareThreshold=0" : null
  sensitive   = true
}

output "db_replica_url" {
  description = "Read-only - point read-heavy queries here, never writes. For local/manual use only - CI builds this itself from 'doctl databases replica get'. null when enable_read_replica = false. Sensitive - fetch with: terraform output -raw db_replica_url"
  value       = var.enable_read_replica ? replace(digitalocean_database_replica.read[0].private_uri, "postgresql://", "jdbc:postgresql://") : null
  sensitive   = true
}

# Deterministic names (derived straight from name_prefix, not from the resources
# themselves) - these are what CI actually needs as GitHub Actions VARIABLES
# (not secrets - they're identifiers, not credentials). Unlike db_password/db_url
# above, they never change across a destroy/recreate cycle, so they're set once
# and never touched again even while you're actively spinning DOKS up and down.
output "db_cluster_name" {
  description = "-> GitHub Actions Variable DO_DB_CLUSTER_NAME"
  value       = digitalocean_database_cluster.postgres.name
}

output "db_pool_name" {
  description = "-> GitHub Actions Variable DO_DB_POOL_NAME. Always the deterministic name, regardless of enable_connection_pool."
  value       = "${var.name_prefix}-pool"
}

output "db_replica_name" {
  description = "-> GitHub Actions Variable DO_DB_REPLICA_NAME. Always the deterministic name, regardless of enable_read_replica - cd-doks.yml checks 'doctl databases replica list' for an actual match before using it."
  value       = "${var.name_prefix}-postgres-replica"
}
