import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import RegisterPage from '../RegisterPage'
import { register, resendOtp, verifyEmailOtp, verifyMobileOtp, login, createRegistrationFeeOrder, verifyRegistrationFee } from '../../api'
import { AuthProvider } from '../../auth'

vi.mock('../../api', async () => {
  const actual = await vi.importActual<typeof import('../../api')>('../../api')
  return {
    ...actual, register: vi.fn(), verifyEmailOtp: vi.fn(), verifyMobileOtp: vi.fn(), resendOtp: vi.fn(),
    login: vi.fn(), createRegistrationFeeOrder: vi.fn(), verifyRegistrationFee: vi.fn(),
    // Avoid a real network call from RegisterPage's own useEffect in every test.
    getRegistrationFee: vi.fn().mockResolvedValue(500),
  }
})

function renderPage() {
  return render(<MemoryRouter><AuthProvider><RegisterPage /></AuthProvider></MemoryRouter>)
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText('you@example.com'), 'newuser@example.com')
  await user.type(screen.getByPlaceholderText('9876543210'), '9876543210')
  // Password / Confirm password share no placeholder — they're the two password-type inputs, in DOM order.
  const [password, confirmPassword] = document.querySelectorAll('input[type="password"]')
  await user.type(password, 'Test@1234')
  await user.type(confirmPassword, 'Test@1234')
}

describe('RegisterPage', () => {
  it('submitting the register form calls register() with the right payload and moves to the OTP step', async () => {
    vi.mocked(register).mockResolvedValue('Registration successful.')
    const user = userEvent.setup()
    renderPage()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /register/i }))

    await waitFor(() => expect(register).toHaveBeenCalledWith({
      email: 'newuser@example.com', mobileNumber: '9876543210',
      password: 'Test@1234', confirmPassword: 'Test@1234',
    }))
    expect(await screen.findByText(/verify email/i)).toBeInTheDocument()
    expect(screen.getByText(/we sent a one-time code to newuser@example.com/i)).toBeInTheDocument()
  }, 15000)

  it('on a "not verified" error, shows the resend-OTP link and it calls resendOtp', async () => {
    vi.mocked(register).mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: 'Account exists but email is not verified.' } },
    })
    vi.mocked(resendOtp).mockResolvedValue('OTP resent.')
    const user = userEvent.setup()
    renderPage()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /register/i }))

    const resendLink = await screen.findByRole('button', { name: /resend otp and continue verifying that account/i })
    await user.click(resendLink)

    await waitFor(() => expect(resendOtp).toHaveBeenCalledWith('newuser@example.com'))
    expect(await screen.findByText(/verify email/i)).toBeInTheDocument()
  }, 15000)

  it('after email OTP verifies, moves to the mobile-OTP step and then the pay-fee step', async () => {
    vi.mocked(register).mockResolvedValue('Registration successful.')
    vi.mocked(verifyEmailOtp).mockResolvedValue('Email verified successfully')
    vi.mocked(verifyMobileOtp).mockResolvedValue('Mobile verified successfully')
    vi.mocked(login).mockResolvedValue({
      userId: '1', email: 'newuser@example.com', mobileNumber: '9876543210',
      token: 'tok', role: 'USER', active: true, registrationFeePaid: false,
    })
    const user = userEvent.setup()
    renderPage()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /register/i }))
    await screen.findByText(/verify email/i)

    await user.type(screen.getByPlaceholderText('6-digit code'), '123456')
    await user.click(screen.getByRole('button', { name: /verify/i }))

    await waitFor(() => expect(verifyEmailOtp).toHaveBeenCalledWith('newuser@example.com', '123456'))
    expect(await screen.findByText(/verify mobile number/i)).toBeInTheDocument()
    expect(screen.getByText(/we sent a one-time code to 9876543210/i)).toBeInTheDocument()

    await user.type(screen.getByPlaceholderText('6-digit code'), '654321')
    await user.click(screen.getByRole('button', { name: /verify/i }))

    await waitFor(() => expect(verifyMobileOtp).toHaveBeenCalledWith('newuser@example.com', '654321'))
    await waitFor(() => expect(login).toHaveBeenCalledWith('newuser@example.com', 'Test@1234'))
    expect(await screen.findByRole('heading', { name: /complete registration/i })).toBeInTheDocument()

    // Clicking "Pay ... to complete registration" starts a real Razorpay order — verified up to
    // that point; actually completing the Checkout modal is Razorpay's own hosted UI, out of scope
    // for this test (see RazorpayCheckout.tsx).
    vi.mocked(createRegistrationFeeOrder).mockResolvedValue({
      orderId: 'order_test123', amountPaise: 50000, currency: 'INR', keyId: 'rzp_test_dummy',
    })
    await user.click(screen.getByRole('button', { name: /pay.*complete registration/i }))
    await waitFor(() => expect(createRegistrationFeeOrder).toHaveBeenCalled())
    expect(verifyRegistrationFee).not.toHaveBeenCalled()
  }, 15000)
})
