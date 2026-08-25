resource "digitalocean_container_registry" "this" {
  count                  = var.create_registry ? 1 : 0
  name                   = var.registry_name
  subscription_tier_slug = var.registry_tier
  region                 = var.region
}

data "digitalocean_container_registry" "existing" {
  count = var.create_registry ? 0 : 1
  name  = var.registry_name
}

locals {
  registry_name = var.create_registry ? digitalocean_container_registry.this[0].name : data.digitalocean_container_registry.existing[0].name
  registry_endpoint = var.create_registry ? digitalocean_container_registry.this[0].endpoint : data.digitalocean_container_registry.existing[0].endpoint
}
