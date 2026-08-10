# DO has one container registry per account (team). It is global, not per-region.
# Images are accessed at registry.digitalocean.com/<name>/<image>:<tag>.
resource "digitalocean_container_registry" "this" {
  name                   = "${var.prefix}-registry"
  subscription_tier_slug = var.registry_tier
  region                 = var.region
}