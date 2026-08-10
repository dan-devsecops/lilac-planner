variable "prefix" {
  description = "Short name prefix for all resources."
  type        = string
  default     = "lilac"
}

variable "region" {
  description = "AWS region."
  type        = string
  default     = "eu-west-1"
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default = {
    app        = "lilac-planner"
    managed-by = "terraform"
  }
}

# ---------------- EKS ----------------
variable "kubernetes_version" {
  description = "EKS cluster version."
  type        = string
  default     = "1.30"
}

variable "node_count" {
  description = "Desired node count in the managed node group."
  type        = number
  default     = 2
}

variable "node_instance_type" {
  description = "EC2 instance type for worker nodes."
  type        = string
  default     = "t3.medium"
}

# ---------------- Database (RDS MariaDB) ----------------
variable "db_engine_version" {
  description = "RDS MariaDB engine version."
  type        = string
  default     = "11.4"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Application database name."
  type        = string
  default     = "lilac_planner"
}

variable "db_admin_user" {
  description = "RDS master username."
  type        = string
  default     = "planner"
}

# ---------------- GitHub OIDC (CI/CD) ----------------
variable "enable_github_oidc" {
  description = "Create the GitHub OIDC provider + CI role."
  type        = bool
  default     = true
}

variable "github_repository" {
  description = "GitHub repo in 'owner/name' form for the OIDC trust policy."
  type        = string
  default     = ""
}
