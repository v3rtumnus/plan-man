import Login from '../main/login'
import Dashboard from '../main/dashboard'
import CreditPlan from "../main/credit/plan";
import CreditOverview from "../main/credit/overview";
import ExpensesDetails from "../main/expenses/detail";
import ExpenseOverview from "../main/expenses/overview";

const routes =[
  {
    path:'/login',
    component: Login,
    isPrivate: false
  },
  {
    path:'/dashboard',
    component: Dashboard,
    isPrivate: true
  },
  {
    path:'/credit/plan',
    component: CreditPlan,
    isPrivate: true
  },
  {
    path:'/credit/overview',
    component: CreditOverview,
    isPrivate: true
  },
  {
    path:'/expenses/overview',
    component: ExpenseOverview,
    isPrivate: true
  },
  {
    path:'/expenses/detail',
    component: ExpensesDetails,
    isPrivate: true
  },
  {
    path:'/*',
    component: Dashboard,
    isPrivate: true
  },
]
 
export default routes
