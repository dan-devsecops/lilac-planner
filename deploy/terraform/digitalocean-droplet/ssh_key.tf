resource "digitalocean_ssh_key" "deploy" {
  name       = "${var.name_prefix}-key"
  public_key = file(pathexpand(var.ssh_public_key_path))
}
