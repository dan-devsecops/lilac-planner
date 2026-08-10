# Look up the latest patch for the requested minor version.
data "digitalocean_kubernetes_versions" "main" {
  version_prefix = "${var.kubernetes_version_prefix}."
}

# Dedicated VPC so the cluster and database share a private network.
resource "digitalocean_vpc" "this" {
  name   = "${var.prefix}-vpc"
  region = var.region
}

resource "digitalocean_kubernetes_cluster" "this" {
  name    = "${var.prefix}-doks"
  region  = var.region
  version = data.digitalocean_kubernetes_versions.main.latest_version

  vpc_uuid = digitalocean_vpc.this.id

  node_pool {
    name       = "${var.prefix}-workers"
    size       = var.node_size
    node_count = var.node_count
    auto_scale = false

    labels = {
      app        = "lilac-planner"
      managed-by = "terraform"
    }
  }
}
