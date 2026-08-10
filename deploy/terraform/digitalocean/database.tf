# DO Managed MySQL 8 - MariaDB JDBC driver is fully compatible with MySQL 8.
# Password is generated and managed by DO; retrieved via the user resource output.
resource "digitalocean_database_cluster" "this" {
  name       = "${var.prefix}-mysql"
  engine     = "mysql"
  version    = "8"
  size       = var.db_size
  region     = var.region
  node_count = 1

  # Place the DB in the same VPC so the cluster can reach it on the private network.
  private_network_uuid = digitalocean_vpc.this.id
}

resource "digitalocean_database_db" "app" {
  cluster_id = digitalocean_database_cluster.this.id
  name       = var.db_name
}

resource "digitalocean_database_user" "app" {
  cluster_id = digitalocean_database_cluster.this.id
  name       = var.db_admin_user
}

# Allow only the DOKS cluster to reach the database.
resource "digitalocean_database_firewall" "this" {
  cluster_id = digitalocean_database_cluster.this.id

  rule {
    type  = "k8s"
    value = digitalocean_kubernetes_cluster.this.id
  }
}