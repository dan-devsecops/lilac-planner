# DO's firewalls and SSH keys resources are NOT project-assignable

resource "digitalocean_project" "this" {
  name        = var.project_name
  description = "Lilac Planner"
  purpose     = "Web Application"
  environment = "Production"

  resources = [digitalocean_droplet.app.urn]
}
