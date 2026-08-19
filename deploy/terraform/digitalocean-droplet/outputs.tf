output "droplet_ip" {
  description = "Public Droplet IPv4"
  value       = digitalocean_droplet.app.ipv4_address
}

output "droplet_id" {
  value = digitalocean_droplet.app.id
}

output "ssh_key_fingerprint" {
  value = digitalocean_ssh_key.deploy.fingerprint
}

output "project_id" {
  value = digitalocean_project.this.id
}
