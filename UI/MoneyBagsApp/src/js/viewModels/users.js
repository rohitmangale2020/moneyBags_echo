define(['knockout', 'accUtils', 'ojs/ojarraydataprovider', 'services/userStore', 'appController', 'ojs/ojtable'],
  function (ko, accUtils, ArrayDataProvider, userStore, app) {
    function UsersViewModel() {
      this.users = userStore.users;
      this.dataProvider = new ArrayDataProvider(this.users, { keyAttributes: 'id' });
      this.columns = [
        { headerText: 'User', field: 'username' }, { headerText: 'Email', field: 'email' },
        { headerText: 'Role', field: 'role' }, { headerText: 'Status', field: 'status' }
      ];
      this.errorMessage = ko.observable('');
      this.createUser = () => { userStore.selectedUser(null); app.goToUserForm(); };
      this.manageUser = (event) => {
        const context = event.detail.context;
        if (context && context.item) { userStore.selectedUser(context.item.data); app.goToUserForm(); }
      };
      this.connected = async () => { accUtils.announce('User directory page loaded.'); document.title = 'MoneyBags | User directory'; try { await userStore.load(); } catch (error) { this.errorMessage(error.message); } };
    }
    return UsersViewModel;
  });
