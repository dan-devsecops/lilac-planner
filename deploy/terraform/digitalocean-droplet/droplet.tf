# Plain Ubuntu box with Docker installed via cloud-init
resource "digitalocean_droplet" "app" {
  name       = "${var.name_prefix}-01"
  region     = var.region
  size       = var.droplet_size
  image      = var.droplet_image
  ssh_keys   = [digitalocean_ssh_key.deploy.fingerprint]
  ipv6       = true
  monitoring = true

  tags = ["lilac-planner", "terraform"]

  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail
    curl -fsSL https://get.docker.com | sh
    mkdir -p /opt/lilac-planner

    ufw allow 22
    ufw allow 80
    ufw allow 443
    ufw --force enable
  EOF
}
