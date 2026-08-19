variable "do_token" {
  description = "DigitalOcean personal access token."
  type        = string
  sensitive   = true
}

variable "name_prefix" {
  description = "Name prefix for all resources."
  type        = string
  default     = "tf-lilac-planner-prod"
}

variable "region" {
  description = "DigitalOcean region slug."
  type        = string
  default     = "fra1"
}

variable "droplet_size" {
  description = "Droplet size slug."
  type        = string
  default     = "s-1vcpu-1gb"
}

variable "droplet_image" {
  description = "Base image slug."
  type        = string
  default     = "ubuntu-24-04-x64"
}

variable "ssh_public_key_path" {
  description = "Path to the PUBLIC half of the keypair to install on the droplet."
  type        = string
  default     = "~/.ssh/id_ed25519_ag_digitalocean_tf.pub"
}

variable "project_name" {
  description = "DO Projects display name in the console"
  type        = string
  default     = "Lilac Planner"
}
