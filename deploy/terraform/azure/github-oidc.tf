# -------------------------------------------------------------
# Federated identity for GitHub Actions (OIDC, no secrets stored).
# Creates an Entra app whose token GitHub can exchange, plus the role
# assignments the CD pipeline needs: push images, fetch the AKS kubeconfig,
# and read DB secrets from Key Vault.
# -------------------------------------------------------------
resource "azuread_application" "github" {
  count        = var.enable_github_oidc ? 1 : 0
  display_name = "${var.prefix}-github-actions"
}

resource "azuread_service_principal" "github" {
  count     = var.enable_github_oidc ? 1 : 0
  client_id = azuread_application.github[0].client_id
}

# Trust tokens from this repo's main branch - covers jobs with no `environment:`
# (cd-azure.yml's "build" job).
resource "azuread_application_federated_identity_credential" "main" {
  count          = var.enable_github_oidc ? 1 : 0
  application_id = azuread_application.github[0].id
  display_name   = "github-main"
  description    = "GitHub Actions deploy from main"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = var.github_oidc_subject != "" ? var.github_oidc_subject : "repo:${var.github_repository}:ref:refs/heads/main"
}

# Separate credential, separate subject format: a job with `environment:`
# set (cd-azure.yml's "deploy" job uses `environment: production`) gets an
# OIDC token subject of "repo:.../...:environment:<name>", not
# ":ref:refs/heads/<branch>" - GitHub issues a different claim shape per
# trigger kind, so one federated credential can't cover both.
resource "azuread_application_federated_identity_credential" "production_environment" {
  count          = var.enable_github_oidc ? 1 : 0
  application_id = azuread_application.github[0].id
  display_name   = "github-production-environment"
  description    = "GitHub Actions deploy via the production environment"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = var.github_oidc_environment_subject != "" ? var.github_oidc_environment_subject : "repo:${var.github_repository}:environment:production"
}

resource "azurerm_role_assignment" "github_acr_push" {
  count                = var.enable_github_oidc ? 1 : 0
  scope                = azurerm_container_registry.this.id
  role_definition_name = "AcrPush"
  principal_id         = azuread_service_principal.github[0].object_id
}

# Grant the pipeline permission to fetch an admin kubeconfig. Must be
# "Azure Kubernetes Service Cluster Admin Role" (grants the ARM action
# listClusterAdminCredential) - NOT "...RBAC Admin" (grants
# listClusterUserCredential, a Kubernetes-API-level authorization role that
# only matters if Azure AD RBAC is enabled on the cluster). This cluster uses
# local accounts and cd-azure.yml calls `az aks get-credentials --admin`, so
# RBAC Admin silently grants nothing usable for that call - the two role
# names differ by one word and are easy to conflate.
#
# Trade-off: this role has no namespace scoping at all (narrower than even
# the old RBAC Admin role was) - it's cluster-wide by nature, since admin
# credentials bypass Kubernetes RBAC entirely. To scope this down, enable
# Azure AD RBAC on the cluster (add an
# azure_active_directory_role_based_access_control block in aks.tf), then:
#   1. Replace this with "Azure Kubernetes Service RBAC Writer" scoped to the
#      namespace resource path:
#        "${azurerm_kubernetes_cluster.this.id}/namespaces/lilac"
#   2. In cd-azure.yml drop the --admin flag from `az aks get-credentials` so
#      the pipeline uses an Entra-backed kubeconfig instead of local accounts.
resource "azurerm_role_assignment" "github_aks" {
  count                = var.enable_github_oidc ? 1 : 0
  scope                = azurerm_resource_group.this.id
  role_definition_name = "Azure Kubernetes Service Cluster Admin Role"
  principal_id         = azuread_service_principal.github[0].object_id
}

