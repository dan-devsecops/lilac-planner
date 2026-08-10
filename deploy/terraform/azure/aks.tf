# Managed Kubernetes cluster. Workload Identity + OIDC issuer are enabled so
# pods can later federate to Entra (e.g. Key Vault CSI / External Secrets)
# without static credentials.
resource "azurerm_kubernetes_cluster" "this" {
  name                = "${var.prefix}-aks"
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
  dns_prefix          = "${var.prefix}-aks"
  kubernetes_version  = var.kubernetes_version

  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  default_node_pool {
    name           = "default"
    node_count     = var.node_count
    vm_size        = var.node_vm_size
    vnet_subnet_id = azurerm_subnet.aks.id
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin = "azure"
    network_policy = "azure"
  }

  tags = var.tags
}
