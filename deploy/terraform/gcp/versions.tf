terraform {
  required_version = ">= 1.6.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state - create the bucket once, then uncomment.
  # backend "gcs" {
  #   bucket = "lilac-tfstate"
  #   prefix = "gcp/lilac-planner"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
