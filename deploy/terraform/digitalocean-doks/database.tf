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
# Applies at the cluster level, so it also covers the replica below (same cluster, same network boundary).
resource "digitalocean_database_firewall" "postgres" {
  cluster_id = digitalocean_database_cluster.postgres.id

  rule {
    type  = "k8s"
    value = digitalocean_kubernetes_cluster.this.id
  }
}

# PgBouncer pool in front of the primary - no extra node, just connection multiplexing.
# App connects to the pool's host/port instead of the cluster's directly; same db_user/password.
resource "digitalocean_database_connection_pool" "app" {
  count      = var.enable_connection_pool ? 1 : 0
  cluster_id = digitalocean_database_cluster.postgres.id
  name       = "${var.name_prefix}-pool"
  mode       = var.db_pool_mode
  size       = var.db_pool_size
  db_name    = digitalocean_database_db.app.name
  user       = digitalocean_database_user.app.name
}

# Queryable read-only replica - distinct from db_node_count's HA standby (that one is
# failover-only and never accepts connections). Shares the primary's db/users/password;
# only the host differs. Point read-heavy, slightly-stale-tolerant queries at it.
resource "digitalocean_database_replica" "read" {
  count                = var.enable_read_replica ? 1 : 0
  cluster_id           = digitalocean_database_cluster.postgres.id
  name                 = "${var.name_prefix}-postgres-replica"
  size                 = var.db_replica_size
  region               = var.region
  private_network_uuid = digitalocean_vpc.this.id
  tags                 = ["lilac-planner", "terraform", "read-replica"]
}
