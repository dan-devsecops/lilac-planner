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
variable "mysql_version" {
  description = "Azure Database for MySQL Flexible Server version."
  type        = string
  default     = "8.0.21"
}

variable "mysql_sku" {
  description = "Flexible Server SKU."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "mysql_storage_gb" {
  description = "Flexible Server storage in GB."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "lilac_planner"
}

variable "db_admin_user" {
  description = "MySQL administrator login."
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
