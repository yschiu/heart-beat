require 'test_helper'

class HeartsControllerTest < ActionController::TestCase
  setup do
    @heart = hearts(:one)
  end

  test "should get index" do
    get :index
    assert_response :success
    assert_not_nil assigns(:hearts)
  end

  test "should get new" do
    get :new
    assert_response :success
  end

  test "should create heart" do
    assert_difference('Heart.count') do
      post :create, heart: { beat: @heart.beat }
    end

    assert_redirected_to heart_path(assigns(:heart))
  end

  test "should show heart" do
    get :show, id: @heart
    assert_response :success
  end

  test "should get edit" do
    get :edit, id: @heart
    assert_response :success
  end

  test "should update heart" do
    patch :update, id: @heart, heart: { beat: @heart.beat }
    assert_redirected_to heart_path(assigns(:heart))
  end

  test "should destroy heart" do
    assert_difference('Heart.count', -1) do
      delete :destroy, id: @heart
    end

    assert_redirected_to hearts_path
  end
end
