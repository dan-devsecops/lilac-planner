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
}

variable "postgres_storage_mb" {
  description = "Flexible Server storage in MB (32768 is the minimum)."
  type        = number
  default     = 32768
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
