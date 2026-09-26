data "azurerm_client_config" "current" {}

# The resource group's own "location" is just metadata for where its
# management history lives - it does NOT need to match the region of the
# resources inside it. Kept fixed (not tied to var.location) so that moving
# AKS/ACR/KeyVault/VNet to a different region (var.location) only replaces
# those individual resources, instead of forcing Terraform to replace the
# whole resource group - which would cascade-destroy everything inside it.
resource "azurerm_resource_group" "this" {
  name     = "${var.prefix}-rg"
  location = "eastus"
  tags     = var.tags
}

# -------------------------------------------------------------
# Networking: one VNet in var.location with a subnet for AKS and a
# delegated subnet for the PostgreSQL Flexible Server (VNet-integrated /
# private access). Both live in the same region since Flexible Server
# requires its delegated subnet to be in the same region as the server.
# -------------------------------------------------------------
resource "azurerm_virtual_network" "this" {
  name                = "${var.prefix}-vnet"
  resource_group_name = azurerm_resource_group.this.name
  location            = var.location
  address_space       = ["10.20.0.0/16"]
  tags                = var.tags
}

resource "azurerm_subnet" "aks" {
  name                 = "aks"
  resource_group_name  = azurerm_resource_group.this.name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = ["10.20.1.0/24"]
}

resource "azurerm_subnet" "postgres" {
  name                 = "postgres"
  resource_group_name  = azurerm_resource_group.this.name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = ["10.20.2.0/24"]
  service_endpoints    = ["Microsoft.Storage"]

  delegation {
    name = "postgres-delegation"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

# Private DNS zone so the Postgres FQDN resolves to its private IP inside the VNet.
resource "azurerm_private_dns_zone" "postgres" {
  name                = "${var.prefix}.private.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.this.name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "${var.prefix}-postgres-link"
  resource_group_name   = azurerm_resource_group.this.name
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.this.id
  tags                  = var.tags
}
