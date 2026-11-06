# Workload Identity for the in-cluster External Secrets Operator (ESO) to read
# lilac-db/lilac-auth values straight from Key Vault - no stored credential,
# same federation pattern as github-oidc.tf but for a Kubernetes ServiceAccount
# instead of a GitHub Actions run. Requires aks.tf's oidc_issuer_enabled /
# workload_identity_enabled = true (provisioned earlier for exactly this).
resource "azurerm_user_assigned_identity" "eso" {
  name                = "${var.prefix}-eso-identity"
  resource_group_name = azurerm_resource_group.this.name
  location            = var.location
  tags                = var.tags
}

# Trusts tokens for the specific ServiceAccount deploy/azure/external-secrets.yaml.tmpl
# creates (system:serviceaccount:<namespace>:<name>) - ESO exchanges that
# token for a real Azure AD token itself (no mutating webhook involved, unlike
# workload-identity-injected application pods).
resource "azurerm_federated_identity_credential" "eso" {
  name                      = "${var.prefix}-eso-federated"
  user_assigned_identity_id = azurerm_user_assigned_identity.eso.id
  audience                  = ["api://AzureADTokenExchange"]
  issuer                    = azurerm_kubernetes_cluster.this.oidc_issuer_url
  subject                   = "system:serviceaccount:lilac-planner:lilac-external-secrets"
}

# Read-only - ESO only ever fetches pg-user/pg-password/pg-url/native-jwt-secret/
# native-admin-password, never writes.
resource "azurerm_role_assignment" "eso_kv_read" {
  scope                = azurerm_key_vault.this.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.eso.principal_id
}
