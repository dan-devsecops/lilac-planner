# Dedicated VPC so the DOKS nodes and the managed database share a private
# network - the database firewall (database.tf) then trusts the cluster
# instead of needing a public DB endpoint.
resource "digitalocean_vpc" "this" {
  name     = "${var.name_prefix}-vpc"
  region   = var.region
  ip_range = "10.30.0.0/16"
}
