variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "prefix" {
  description = "Short name prefix for all resources."
  type        = string
  default     = "lilac"
}

variable "region" {
  description = "GCP region."
  type        = string
  default     = "europe-west1"
}

# ---------------- GKE ----------------
variable "node_count" {
  description = "Nodes per zone in the node pool."
  type        = number
  default     = 1
}

variable "node_machine_type" {
  description = "GKE node machine type."
  type        = string
  default     = "e2-standard-2"
}

# ---------------- Cloud SQL ----------------
variable "db_tier" {
  description = "Cloud SQL machine tier."
  type        = string
  default     = "db-f1-micro"
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "lilac_planner"
}

variable "db_user" {
  description = "Cloud SQL application user."
  type        = string
  default     = "planner"
}

# ---------------- GitHub OIDC (CI/CD) ----------------
variable "enable_github_oidc" {
  description = "Create a Workload Identity Federation pool + service account for GitHub Actions."
  type        = bool
  default     = true
}

variable "github_repository" {
  description = "GitHub repo in 'owner/name' form for the WIF attribute condition."
  type        = string
  default     = ""
}
