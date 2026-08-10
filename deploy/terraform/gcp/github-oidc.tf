# -------------------------------------------------------------
# Workload Identity Federation for GitHub Actions (no exported keys).
# GitHub's OIDC token is exchanged for short-lived credentials of a
# dedicated service account scoped to this repo.
# -------------------------------------------------------------
resource "google_iam_workload_identity_pool" "github" {
  count                     = var.enable_github_oidc ? 1 : 0
  workload_identity_pool_id = "${var.prefix}-gh-pool"
  display_name              = "GitHub Actions"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  count                              = var.enable_github_oidc ? 1 : 0
  workload_identity_pool_id          = google_iam_workload_identity_pool.github[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
  }
  # Only tokens from this repo may use the pool.
  attribute_condition = "assertion.repository == '${var.github_repository}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account" "github" {
  count        = var.enable_github_oidc ? 1 : 0
  account_id   = "${var.prefix}-github-actions"
  display_name = "GitHub Actions deployer"
}

# Let identities from this repo impersonate the deployer SA.
resource "google_service_account_iam_member" "github_wif" {
  count              = var.enable_github_oidc ? 1 : 0
  service_account_id = google_service_account.github[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[0].name}/attribute.repository/${var.github_repository}"
}

# What the deployer SA can do: push images, deploy to GKE, read DB secrets.
resource "google_project_iam_member" "github" {
  for_each = var.enable_github_oidc ? toset([
    "roles/artifactregistry.writer",
    "roles/container.developer",
    "roles/secretmanager.secretAccessor",
  ]) : toset([])
  project = var.project_id
  role    = each.key
  member  = "serviceAccount:${google_service_account.github[0].email}"
}
