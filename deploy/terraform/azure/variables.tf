variable "prefix" {
  description = "Short name prefix for all resources (lowercase, 3-12 chars)."
  type        = string
  default     = "lilac"
}

variable "location" {
  description = "Azure region."
  type        = string
  default     = "westeurope"
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default = {
    app        = "lilac-planner"
    managed-by = "terraform"
  }
}

# ---------------- AKS ----------------
variable "kubernetes_version" {
  description = "AKS control plane version. Leave null for the region default."
  type        = string
  default     = null
}

variable "node_count" {
  description = "Number of nodes in the default pool."
  type        = number
  default     = 2
}

variable "node_vm_size" {
  description = "VM size for AKS nodes."
  type        = string
  default     = "Standard_B2s"
}

# ---------------- Database ----------------
variable "postgres_version" {
  description = "Azure Database for PostgreSQL Flexible Server version."
  type        = string
  default     = "16"
}

variable "postgres_sku" {
  description = "Flexible Server SKU. Must be a General Purpose (GP_*) or Memory Optimized (MO_*) tier - Burstable (B_*) does not support zone-redundant HA."
  type        = string
  default     = "GP_Standard_D2s_v3"

  validation {
    condition     = can(regex("^(GP_|MO_)", var.postgres_sku))
    error_message = "postgres_sku must be a General Purpose (GP_*) or Memory Optimized (MO_*) tier - Burstable (B_*) does not support the zone-redundant HA configured in database.tf."
  }
}

variable "postgres_storage_mb" {
  description = "Flexible Server storage in MB (32768 is the minimum)."
  type        = number
  default     = 32768
}

variable "postgres_auto_grow_enabled" {
  description = "Automatically grow storage when nearing capacity, instead of the server going read-only on a full disk."
  type        = bool
  default     = true
}

variable "postgres_geo_redundant_backup_enabled" {
  description = "Replicate backups to the Azure paired region, so a region-wide outage (not just an AZ failure, which zone-redundant HA already covers) is still recoverable. ForceNew: changing this on an existing server requires recreation."
  type        = bool
  default     = true
}

variable "db_name" {
  description = "Primary application database name."
  type        = string
  default     = "lilac_planner"
}

variable "db_name_2" {
  description = "Second database hosted on the same Flexible Server."
  type        = string
  default     = "lilac_planner_2"
}

variable "db_admin_user" {
  description = "PostgreSQL administrator login."
  type        = string
  default     = "planner"
}

# ---------------- GitHub OIDC (CI/CD) ----------------
variable "enable_github_oidc" {
  description = "Create an Entra app + federated credentials so GitHub Actions can deploy without stored passwords."
  type        = bool
  default     = true
}

variable "github_repository" {
  description = "GitHub repo in 'owner/name' form, used for the OIDC federated credential subject."
  type        = string
  default     = ""
}

variable "github_oidc_subject" {
  description = <<-EOT
    Exact OIDC subject GitHub issues for a job with no `environment:` set
    (e.g. cd-azure.yml's "build" job). Empty -> falls back to the
    name-based format.
  EOT
  type        = string
  default     = ""
}

variable "github_oidc_environment_subject" {
  description = <<-EOT
    Same idea as github_oidc_subject, but for a job that sets
    `environment: production` (cd-azure.yml's "deploy" job) - GitHub issues
    a different claim shape there ("...:environment:production" instead of
    "...:ref:refs/heads/main"), needing its own federated credential. Empty
    -> falls back to the name-based format.
  EOT
  type        = string
  default     = ""
}
