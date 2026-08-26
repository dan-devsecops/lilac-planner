resource "digitalocean_project" "this" {
  name        = var.project_name
  description = "Lilac Planner - Kubernetes (DOKS)"
  purpose     = "Web Application"
  environment = "Production"

  resources = [
    digitalocean_kubernetes_cluster.this.urn,
    digitalocean_database_cluster.postgres.urn,
  ]
}
