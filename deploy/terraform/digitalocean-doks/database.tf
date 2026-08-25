# Managed PostgreSQL - private networking only (private_network_uuid), no public endpoint.

resource "digitalocean_database_cluster" "postgres" {
  name                 = "${var.name_prefix}-postgres"
  engine               = "pg"
  version              = var.db_version
  size                 = var.db_size
  region               = var.region
  node_count           = var.db_node_count
  private_network_uuid = digitalocean_vpc.this.id
  tags                 = ["lilac-planner", "terraform"]
}

resource "digitalocean_database_db" "app" {
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = var.db_name
}

# A dedicated app user (not the cluster admin) with a DO-generated password.
resource "digitalocean_database_user" "app" {
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = var.db_user
}

# Only the DOKS cluster may reach the database - no public exposure, no per-droplet IP allowlisting to maintain.
resource "digitalocean_database_firewall" "postgres" {
  cluster_id = digitalocean_database_cluster.postgres.id

  rule {
    type  = "k8s"
    value = digitalocean_kubernetes_cluster.this.id
  }
}
