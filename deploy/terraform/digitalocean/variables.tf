variable "do_token" {
  description = "DigitalOcean personal access token. Supply via TF_VAR_do_token - never commit."
  type        = string
  sensitive   = true
}

variable "prefix" {
  description = "Short name prefix for all resources."
  type        = string
  default     = "lilac"
}

variable "region" {
  description = "DigitalOcean region slug (e.g. fra1, nyc3, ams3)."
  type        = string
  default     = "fra1"
}

# ---------- DOKS ----------

variable "kubernetes_version_prefix" {
  description = "DOKS version prefix - the latest available patch is picked automatically (e.g. '1.30')."
  type        = string
  default     = "1.30"
}

variable "node_size" {
  description = "Droplet size slug for worker nodes (run: doctl kubernetes options sizes)."
  type        = string
  default     = "s-2vcpu-4gb"
}

variable "node_count" {
  description = "Number of worker nodes."
  type        = number
  default     = 2
}

# ---------- Database (DO Managed MySQL) ----------

variable "db_size" {
  description = "Managed-database size slug (run: doctl databases list-options --engine mysql)."
  type        = string
  default     = "db-s-1vcpu-1gb"
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "lilac_planner"
}

variable "db_admin_user" {
  description = "Database user for the application."
  type        = string
  default     = "planner"
}

# ---------- Registry ----------

variable "registry_tier" {
  description = "DOCR subscription tier: starter (1 repo / 500 MB free), basic ($5/mo, 5 GB), professional ($20/mo, 100 GB)."
  type        = string
  default     = "basic"
}
