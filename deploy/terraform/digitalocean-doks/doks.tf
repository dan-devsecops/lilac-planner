# Latest supported minor's newest patch, unless kubernetes_version pins one.
data "digitalocean_kubernetes_versions" "this" {
  version_prefix = "1."
}

locals {
  k8s_version = var.kubernetes_version != "" ? var.kubernetes_version : data.digitalocean_kubernetes_versions.this.latest_version
}

resource "digitalocean_kubernetes_cluster" "this" {
  name     = "${var.name_prefix}-cluster"
  region   = var.region
  version  = local.k8s_version
  vpc_uuid = digitalocean_vpc.this.id
  ha       = var.ha_control_plane

  node_pool {
    name       = "default"
    size       = var.node_size
    auto_scale = var.autoscale
    node_count = var.autoscale ? null : var.node_count
    min_nodes  = var.autoscale ? var.min_nodes : null
    max_nodes  = var.autoscale ? var.max_nodes : null
    tags       = ["lilac-planner", "terraform"]
  }

  tags = ["lilac-planner", "terraform"]
}
