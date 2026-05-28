import api from './index'

export interface DirEntry {
  name: string
  path: string
}

export function browseDirectory(parentPath?: string) {
  return api.get<any, { data: DirEntry[] }>('/filesystem/browse', {
    params: parentPath ? { path: parentPath } : {}
  })
}
