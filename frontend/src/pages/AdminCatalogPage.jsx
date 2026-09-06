import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  createAdminService, createAdminSkill, deleteAdminService, deleteAdminSkill,
  fetchAdminServices, fetchAdminSkills, updateAdminService, updateAdminSkill,
} from '../api/admin.js'
import { extractErrorMessage } from '../api/client.js'
import { Spinner } from '../components/Spinner.jsx'
import { EditIcon, TrashIcon, WrenchIcon } from '../components/icons.jsx'

const EMPTY_SERVICE_FORM = { serviceName: '', category: '', description: '' }
const EMPTY_SKILL_FORM = { skillName: '', description: '' }

export default function AdminCatalogPage() {
  const [tab, setTab] = useState('services')

  return (
    <div className="container">
      <h1 className="h3 mb-1 fade-in-up d-flex align-items-center gap-2">
        <WrenchIcon /> Services &amp; skills
      </h1>
      <p className="text-muted">
        <Link to="/admin">← Back to dashboard</Link>
      </p>

      <ul className="nav nav-tabs mb-3">
        <li className="nav-item">
          <button className={`nav-link ${tab === 'services' ? 'active' : ''}`} onClick={() => setTab('services')}>
            Services
          </button>
        </li>
        <li className="nav-item">
          <button className={`nav-link ${tab === 'skills' ? 'active' : ''}`} onClick={() => setTab('skills')}>
            Skills
          </button>
        </li>
      </ul>

      {tab === 'services' ? <ServicesManager /> : <SkillsManager />}
    </div>
  )
}

function ServicesManager() {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [form, setForm] = useState(EMPTY_SERVICE_FORM)
  const [editingId, setEditingId] = useState(null)
  const [saving, setSaving] = useState(false)
  const [confirmingDeleteId, setConfirmingDeleteId] = useState(null)
  const [deleteError, setDeleteError] = useState('')

  const load = useCallback(async () => {
    try {
      setError('')
      setItems(await fetchAdminServices())
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load services.'))
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  function startEdit(service) {
    setEditingId(service.serviceId)
    setForm({
      serviceName: service.serviceName,
      category: service.category || '',
      description: service.description || '',
    })
    setFormError('')
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_SERVICE_FORM)
    setFormError('')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      if (editingId) {
        const updated = await updateAdminService(editingId, form)
        setItems((prev) => prev.map((s) => (s.serviceId === updated.serviceId ? updated : s)))
      } else {
        const created = await createAdminService(form)
        setItems((prev) => [...prev, created])
      }
      cancelEdit()
    } catch (err) {
      setFormError(extractErrorMessage(err, 'Could not save this service.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(service) {
    if (confirmingDeleteId !== service.serviceId) {
      setConfirmingDeleteId(service.serviceId)
      return
    }
    setDeleteError('')
    try {
      await deleteAdminService(service.serviceId)
      setItems((prev) => prev.filter((s) => s.serviceId !== service.serviceId))
    } catch (err) {
      setDeleteError(extractErrorMessage(err, 'Could not delete this service.'))
    } finally {
      setConfirmingDeleteId(null)
    }
  }

  if (error) return <div className="alert alert-danger">{error}</div>
  if (!items) return <Spinner label="Loading services…" />

  return (
    <div className="row g-4">
      <div className="col-md-7">
        {deleteError && <div className="alert alert-danger">{deleteError}</div>}
        {items.length === 0 && <p className="text-muted">No services yet — add one to get started.</p>}
        <div className="d-flex flex-column gap-2">
          {items.map((s) => (
            <div className="card p-3" key={s.serviceId}>
              <div className="d-flex justify-content-between align-items-start gap-2">
                <div>
                  <div className="fw-semibold">{s.serviceName}</div>
                  {s.category && <div className="text-muted small">{s.category}</div>}
                  {s.description && <div className="small mt-1">{s.description}</div>}
                </div>
                <div className="d-flex gap-1 flex-shrink-0">
                  <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => startEdit(s)}>
                    <EditIcon width={14} height={14} />
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${confirmingDeleteId === s.serviceId ? 'btn-danger' : 'btn-outline-danger'}`}
                    onClick={() => handleDelete(s)}
                  >
                    {confirmingDeleteId === s.serviceId ? 'Confirm?' : <TrashIcon width={14} height={14} />}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="col-md-5">
        <div className="card p-3">
          <h2 className="h6 mb-3">{editingId ? 'Edit service' : 'Add a service'}</h2>
          {formError && <div className="alert alert-danger py-2">{formError}</div>}
          <form onSubmit={handleSubmit}>
            <div className="mb-2">
              <label className="form-label small mb-1">Name</label>
              <input
                type="text"
                className="form-control"
                required
                value={form.serviceName}
                onChange={(e) => setForm({ ...form, serviceName: e.target.value })}
              />
            </div>
            <div className="mb-2">
              <label className="form-label small mb-1">Category</label>
              <input
                type="text"
                className="form-control"
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
              />
            </div>
            <div className="mb-3">
              <label className="form-label small mb-1">Description</label>
              <textarea
                className="form-control"
                rows={2}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </div>
            <div className="d-flex gap-2">
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : editingId ? 'Save changes' : 'Add service'}
              </button>
              {editingId && (
                <button type="button" className="btn btn-outline-secondary" onClick={cancelEdit}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

function SkillsManager() {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [form, setForm] = useState(EMPTY_SKILL_FORM)
  const [editingId, setEditingId] = useState(null)
  const [saving, setSaving] = useState(false)
  const [confirmingDeleteId, setConfirmingDeleteId] = useState(null)
  const [deleteError, setDeleteError] = useState('')

  const load = useCallback(async () => {
    try {
      setError('')
      setItems(await fetchAdminSkills())
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load skills.'))
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  function startEdit(skill) {
    setEditingId(skill.skillId)
    setForm({ skillName: skill.skillName, description: skill.description || '' })
    setFormError('')
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_SKILL_FORM)
    setFormError('')
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      if (editingId) {
        const updated = await updateAdminSkill(editingId, form)
        setItems((prev) => prev.map((s) => (s.skillId === updated.skillId ? updated : s)))
      } else {
        const created = await createAdminSkill(form)
        setItems((prev) => [...prev, created])
      }
      cancelEdit()
    } catch (err) {
      setFormError(extractErrorMessage(err, 'Could not save this skill.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(skill) {
    if (confirmingDeleteId !== skill.skillId) {
      setConfirmingDeleteId(skill.skillId)
      return
    }
    setDeleteError('')
    try {
      await deleteAdminSkill(skill.skillId)
      setItems((prev) => prev.filter((s) => s.skillId !== skill.skillId))
    } catch (err) {
      setDeleteError(extractErrorMessage(err, 'Could not delete this skill.'))
    } finally {
      setConfirmingDeleteId(null)
    }
  }

  if (error) return <div className="alert alert-danger">{error}</div>
  if (!items) return <Spinner label="Loading skills…" />

  return (
    <div className="row g-4">
      <div className="col-md-7">
        {deleteError && <div className="alert alert-danger">{deleteError}</div>}
        {items.length === 0 && <p className="text-muted">No skills yet — add one to get started.</p>}
        <div className="d-flex flex-column gap-2">
          {items.map((s) => (
            <div className="card p-3" key={s.skillId}>
              <div className="d-flex justify-content-between align-items-start gap-2">
                <div>
                  <div className="fw-semibold">{s.skillName}</div>
                  {s.description && <div className="small mt-1 text-muted">{s.description}</div>}
                </div>
                <div className="d-flex gap-1 flex-shrink-0">
                  <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => startEdit(s)}>
                    <EditIcon width={14} height={14} />
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${confirmingDeleteId === s.skillId ? 'btn-danger' : 'btn-outline-danger'}`}
                    onClick={() => handleDelete(s)}
                  >
                    {confirmingDeleteId === s.skillId ? 'Confirm?' : <TrashIcon width={14} height={14} />}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="col-md-5">
        <div className="card p-3">
          <h2 className="h6 mb-3">{editingId ? 'Edit skill' : 'Add a skill'}</h2>
          {formError && <div className="alert alert-danger py-2">{formError}</div>}
          <form onSubmit={handleSubmit}>
            <div className="mb-2">
              <label className="form-label small mb-1">Name</label>
              <input
                type="text"
                className="form-control"
                required
                value={form.skillName}
                onChange={(e) => setForm({ ...form, skillName: e.target.value })}
              />
            </div>
            <div className="mb-3">
              <label className="form-label small mb-1">Description</label>
              <textarea
                className="form-control"
                rows={2}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </div>
            <div className="d-flex gap-2">
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : editingId ? 'Save changes' : 'Add skill'}
              </button>
              {editingId && (
                <button type="button" className="btn btn-outline-secondary" onClick={cancelEdit}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
